package hk.ljx.fishhub.user.relation.biz.cache;

import cn.hutool.core.collection.CollUtil;
import hk.ljx.framework.common.util.CacheTtl;
import hk.ljx.framework.common.util.DateUtils;
import hk.ljx.fishhub.user.relation.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.user.relation.biz.domain.dataobject.FollowingDO;
import hk.ljx.fishhub.user.relation.biz.domain.mapper.FollowingDOMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


@Component
@Slf4j
public class RelationListCacheService {

    /** 关注列表展示上限（与关注数上限一致） */
    public static final int FOLLOWING_LIST_MAX = 2000;
    /** 粉丝列表展示上限 */
    public static final int FANS_LIST_MAX = 5000;

    private static final long REBUILD_LOCK_SECONDS = 60L;

    private static DefaultRedisScript<Long> luaScript(String luaPath) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(luaPath)));
        script.setResultType(Long.class);
        return script;
    }

    private static final DefaultRedisScript<Long> FOLLOW_BATCH_ADD_AND_EXPIRE_SCRIPT = luaScript("/lua/follow_batch_add_and_expire.lua");
    private static final DefaultRedisScript<Long> FANS_ADD_SCRIPT = luaScript("/lua/fans_add.lua");
    private static final DefaultRedisScript<Long> FANS_REMOVE_SCRIPT = luaScript("/lua/fans_remove.lua");
    private static final DefaultRedisScript<Long> FANS_BATCH_ADD_AND_EXPIRE_SCRIPT = luaScript("/lua/fans_batch_add_and_expire.lua");
    private static final DefaultRedisScript<Long> EMPTY_ZSET_SCRIPT = luaScript("/lua/zset_empty_with_expire.lua");

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private FollowingDOMapper followingDOMapper;

    /** 关注列表一页（offset 从 0 开始，最多 count 条）。缓存未命中先单飞重建，兜底 DB。 */
    public List<String> fetchFollowingMembers(Long userId, long offset, int count) {
        String key = RedisKeyConstants.buildUserFollowingKey(userId);
        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                return range(key, offset, count);
            }
            ensureFollowingCache(userId);
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                return range(key, offset, count);
            }
        } catch (Exception e) {
            log.warn("读取关注列表缓存失败，回源 DB, userId={}", userId, e);
        }
        return followingFromDb(userId, offset, count);
    }

    /** 粉丝列表一页（offset 从 0 开始，最多 count 条） */
    public List<String> fetchFansMembers(Long userId, long offset, int count) {
        String key = RedisKeyConstants.buildUserFansKey(userId);
        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                return range(key, offset, count);
            }
            ensureFansCache(userId);
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                return range(key, offset, count);
            }
        } catch (Exception e) {
            log.warn("读取粉丝列表缓存失败，回源 DB, userId={}", userId, e);
        }
        return fansFromDb(userId, offset, count);
    }

    /** 粉丝新增：仅当 fans ZSet 已存在时增量写入并续期 */
    public void addFan(Long targetUserId, Long fanUserId, LocalDateTime createTime) {
        try {
            stringRedisTemplate.execute(FANS_ADD_SCRIPT, Collections.singletonList(RedisKeyConstants.buildUserFansKey(targetUserId)),
                    String.valueOf(DateUtils.localDateTime2Timestamp(createTime)), String.valueOf(fanUserId), String.valueOf(ttlSeconds()));
        } catch (Exception e) {
            log.warn("粉丝 ZSet 增量写入失败（读侧重建兜底）, target={}, fan={}", targetUserId, fanUserId, e);
        }
    }

    /** 粉丝取关：仅当 fans ZSet 已存在时移除并续期 */
    public void removeFan(Long targetUserId, Long fanUserId) {
        try {
            stringRedisTemplate.execute(FANS_REMOVE_SCRIPT, Collections.singletonList(RedisKeyConstants.buildUserFansKey(targetUserId)),
                    String.valueOf(fanUserId), String.valueOf(ttlSeconds()));
        } catch (Exception e) {
            log.warn("粉丝 ZSet 移除失败（读侧重建兜底）, target={}, fan={}", targetUserId, fanUserId, e);
        }
    }

    /** 当前用户已关注的候选用户集合：优先读 following ZSet，缺失回源 DB */
    public Set<Long> findFollowedUserIds(Long userId, Collection<Long> candidates) {
        if (Objects.isNull(userId) || CollUtil.isEmpty(candidates)) {
            return Collections.emptySet();
        }
        String key = RedisKeyConstants.buildUserFollowingKey(userId);
        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                Set<String> all = stringRedisTemplate.opsForZSet().range(key, 0, -1);
                if (CollUtil.isEmpty(all)) {
                    return Collections.emptySet();
                }
                Set<String> wanted = candidates.stream().map(String::valueOf).collect(Collectors.toSet());
                all.retainAll(wanted);
                return all.stream().map(Long::valueOf).collect(Collectors.toSet());
            }
        } catch (Exception e) {
            log.warn("读取关注 ZSet 失败, userId={}", userId, e);
        }
        List<Long> ids = followingDOMapper.selectFollowingUserIds(userId, new ArrayList<>(candidates));
        return new HashSet<>(ids);
    }

    /** 构建关注/粉丝 ZSet 批量写入参数：(score, member) 成对，末位为过期秒数 */
    public static String[] buildMemberArgs(List<FollowingDO> records, Function<FollowingDO, Long> memberMapper, long expireSeconds) {
        String[] args = new String[records.size() * 2 + 1];
        int i = 0;
        for (FollowingDO record : records) {
            args[i++] = String.valueOf(DateUtils.localDateTime2Timestamp(record.getCreateTime()));
            args[i++] = String.valueOf(memberMapper.apply(record));
        }
        args[args.length - 1] = String.valueOf(expireSeconds);
        return args;
    }

    private static long ttlSeconds() {
        return CacheTtl.days(7, 1);
    }

    private void ensureFollowingCache(Long userId) {
        String key = RedisKeyConstants.buildUserFollowingKey(userId);
        String lockKey = RedisKeyConstants.buildFollowingRebuildLockKey(userId);
        if (!tryAcquireRebuildLock(lockKey)) {
            waitForListKey(key);
            return;
        }
        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                return;
            }
            List<FollowingDO> records = followingDOMapper.selectByUserId(userId);
            if (CollUtil.isEmpty(records)) {
                stringRedisTemplate.execute(EMPTY_ZSET_SCRIPT, Collections.singletonList(key), String.valueOf(ttlSeconds()));
                return;
            }
            List<FollowingDO> capped = records.size() > FOLLOWING_LIST_MAX ? records.subList(0, FOLLOWING_LIST_MAX) : records;
            stringRedisTemplate.execute(FOLLOW_BATCH_ADD_AND_EXPIRE_SCRIPT, Collections.singletonList(key),
                    (Object[]) buildMemberArgs(capped, FollowingDO::getFollowingUserId, ttlSeconds()));
        } finally {
            releaseRebuildLock(lockKey);
        }
    }

    private void ensureFansCache(Long userId) {
        String key = RedisKeyConstants.buildUserFansKey(userId);
        String lockKey = RedisKeyConstants.buildFansRebuildLockKey(userId);
        if (!tryAcquireRebuildLock(lockKey)) {
            waitForListKey(key);
            return;
        }
        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                return;
            }
            List<FollowingDO> records = followingDOMapper.selectCursorPageByFollowingUserId(userId, null, FANS_LIST_MAX);
            if (CollUtil.isEmpty(records)) {
                stringRedisTemplate.execute(EMPTY_ZSET_SCRIPT, Collections.singletonList(key), String.valueOf(ttlSeconds()));
                return;
            }
            stringRedisTemplate.execute(FANS_BATCH_ADD_AND_EXPIRE_SCRIPT, Collections.singletonList(key),
                    (Object[]) buildMemberArgs(records, FollowingDO::getUserId, ttlSeconds()));
        } finally {
            releaseRebuildLock(lockKey);
        }
    }

    private List<String> range(String key, long offset, int count) {
        if (count <= 0) {
            return Collections.emptyList();
        }
        Set<String> members = stringRedisTemplate.opsForZSet().reverseRange(key, offset, offset + count - 1L);
        if (CollUtil.isEmpty(members)) {
            return Collections.emptyList();
        }
        return new ArrayList<>(members);
    }

    private List<String> followingFromDb(Long userId, long offset, int count) {
        List<FollowingDO> rows = followingDOMapper.selectCursorPageByUserId(userId, null, offset + count);
        return sliceRows(rows, offset, count, FollowingDO::getFollowingUserId);
    }

    private List<String> fansFromDb(Long userId, long offset, int count) {
        List<FollowingDO> rows = followingDOMapper.selectCursorPageByFollowingUserId(userId, null, offset + count);
        return sliceRows(rows, offset, count, FollowingDO::getUserId);
    }

    private List<String> sliceRows(List<FollowingDO> rows, long offset, int count, Function<FollowingDO, Long> mapper) {
        if (CollUtil.isEmpty(rows) || offset >= rows.size()) {
            return Collections.emptyList();
        }
        int from = (int) offset;
        int to = Math.min(rows.size(), from + count);
        List<String> result = new ArrayList<>(to - from);
        for (int i = from; i < to; i++) {
            result.add(String.valueOf(mapper.apply(rows.get(i))));
        }
        return result;
    }

    private boolean tryAcquireRebuildLock(String lockKey) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(REBUILD_LOCK_SECONDS)));
        } catch (Exception e) {
            log.warn("获取列表重建锁失败, lockKey={}", lockKey, e);
            return false;
        }
    }

    private void releaseRebuildLock(String lockKey) {
        try {
            stringRedisTemplate.delete(lockKey);
        } catch (Exception e) {
            log.warn("释放列表重建锁失败, lockKey={}", lockKey, e);
        }
    }

    private void waitForListKey(String key) {
        for (int i = 0; i < 3; i++) {
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                    return;
                }
            } catch (Exception e) {
                log.warn("等待列表缓存重建失败, key={}", key, e);
                return;
            }
        }
    }
}
