package hk.ljx.fishhub.user.relation.biz.cache;

import cn.hutool.core.collection.CollUtil;
import hk.ljx.framework.common.util.CacheTtl;
import hk.ljx.framework.common.util.DateUtils;
import hk.ljx.fishhub.user.relation.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.user.relation.biz.domain.dataobject.FollowingDO;
import hk.ljx.fishhub.user.relation.biz.domain.mapper.FollowingDOMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 用户关注/粉丝列表缓存服务（通俗 Java 原生 ZSet 实现）
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RelationListCacheService {

    /** 关注列表展示上限（与关注数上限一致） */
    public static final int FOLLOWING_LIST_MAX = 2000;
    /** 粉丝列表展示上限 */
    public static final int FANS_LIST_MAX = 5000;

    private final StringRedisTemplate stringRedisTemplate;
    private final FollowingDOMapper followingDOMapper;

    /** 关注列表一页（offset 从 0 开始，最多 count 条）。缓存未命中直接重建，兜底 DB。 */
    public List<String> fetchFollowingMembers(Long userId, long offset, int count) {
        String key = RedisKeyConstants.buildUserFollowingKey(userId);
        try {
            ensureFollowingCache(userId);
            return range(key, offset, count);
        } catch (Exception e) {
            log.warn("读取关注列表缓存失败，回源 DB, userId={}", userId, e);
        }
        return followingFromDb(userId, offset, count);
    }

    /** 粉丝列表一页（offset 从 0 开始，最多 count 条） */
    public List<String> fetchFansMembers(Long userId, long offset, int count) {
        String key = RedisKeyConstants.buildUserFansKey(userId);
        try {
            ensureFansCache(userId);
            return range(key, offset, count);
        } catch (Exception e) {
            log.warn("读取粉丝列表缓存失败，回源 DB, userId={}", userId, e);
        }
        return fansFromDb(userId, offset, count);
    }

    /** 粉丝新增：仅当 fans ZSet 已存在时增量写入并续期 */
    public void addFan(Long targetUserId, Long fanUserId, LocalDateTime createTime) {
        String key = RedisKeyConstants.buildUserFansKey(targetUserId);
        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                double score = DateUtils.localDateTime2Timestamp(createTime);
                stringRedisTemplate.opsForZSet().add(key, String.valueOf(fanUserId), score);
                stringRedisTemplate.expire(key, ttlSeconds(), TimeUnit.SECONDS);

                // 超出 5000 条裁剪久远粉丝
                Long size = stringRedisTemplate.opsForZSet().zCard(key);
                if (size != null && size > FANS_LIST_MAX) {
                    stringRedisTemplate.opsForZSet().removeRange(key, 0, size - FANS_LIST_MAX - 1);
                }
            }
        } catch (Exception e) {
            log.warn("粉丝 ZSet 增量写入失败（读侧重建兜底）, target={}, fan={}", targetUserId, fanUserId, e);
        }
    }

    /** 粉丝取关：仅当 fans ZSet 已存在时移除并续期 */
    public void removeFan(Long targetUserId, Long fanUserId) {
        String key = RedisKeyConstants.buildUserFansKey(targetUserId);
        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                stringRedisTemplate.opsForZSet().remove(key, String.valueOf(fanUserId));
                stringRedisTemplate.expire(key, ttlSeconds(), TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("粉丝 ZSet 移除失败（读侧重建兜底）, target={}, fan={}", targetUserId, fanUserId, e);
        }
    }

    /** 当前用户已关注的候选用户集合：优先读 following ZSet（Pipeline 批量查 score），缺失回源 DB */
    public Set<Long> findFollowedUserIds(Long userId, Collection<Long> candidates) {
        if (Objects.isNull(userId) || CollUtil.isEmpty(candidates)) {
            return Collections.emptySet();
        }
        String key = RedisKeyConstants.buildUserFollowingKey(userId);
        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                List<Long> candidateList = candidates.stream().filter(Objects::nonNull).distinct().toList();
                if (candidateList.isEmpty()) {
                    return Collections.emptySet();
                }
                List<Object> scores = stringRedisTemplate.executePipelined(new SessionCallback<>() {
                    @Override
                    public Object execute(RedisOperations operations) {
                        for (Long candidateId : candidateList) {
                            operations.opsForZSet().score(key, String.valueOf(candidateId));
                        }
                        return null;
                    }
                });
                Set<Long> followed = new HashSet<>();
                for (int i = 0; i < candidateList.size(); i++) {
                    if (scores != null && i < scores.size() && scores.get(i) != null) {
                        followed.add(candidateList.get(i));
                    }
                }
                return followed;
            }
        } catch (Exception e) {
            log.warn("读取关注 ZSet 失败, userId={}", userId, e);
        }
        List<Long> ids = followingDOMapper.selectFollowingUserIds(userId, new ArrayList<>(candidates));
        return new HashSet<>(ids);
    }

    private static long ttlSeconds() {
        return CacheTtl.days(7, 1);
    }

    public void ensureFollowingCache(Long userId) {
        String key = RedisKeyConstants.buildUserFollowingKey(userId);
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            rebuildFollowingCache(key, userId);
        }
    }

    private void rebuildFollowingCache(String key, Long userId) {
        List<FollowingDO> records = followingDOMapper.selectByUserId(userId);
        // 1. 写入防穿透哨兵
        stringRedisTemplate.opsForZSet().add(key, "-1", 0.0);

        // 2. 普通 for 循环组装数据
        if (CollUtil.isNotEmpty(records)) {
            Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
            int limit = Math.min(records.size(), FOLLOWING_LIST_MAX);
            for (int i = 0; i < limit; i++) {
                FollowingDO record = records.get(i);
                String member = String.valueOf(record.getFollowingUserId());
                double score = DateUtils.localDateTime2Timestamp(record.getCreateTime());
                tuples.add(ZSetOperations.TypedTuple.of(member, score));
            }
            stringRedisTemplate.opsForZSet().add(key, tuples);
        }

        // 3. 设置过期时间
        stringRedisTemplate.expire(key, ttlSeconds(), TimeUnit.SECONDS);
    }

    public void ensureFansCache(Long userId) {
        String key = RedisKeyConstants.buildUserFansKey(userId);
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            rebuildFansCache(key, userId);
        }
    }

    private void rebuildFansCache(String key, Long userId) {
        List<FollowingDO> records = followingDOMapper.selectCursorPageByFollowingUserId(userId, null, FANS_LIST_MAX);
        // 1. 写入防穿透哨兵
        stringRedisTemplate.opsForZSet().add(key, "-1", 0.0);

        // 2. 普通 for 循环组装数据
        if (CollUtil.isNotEmpty(records)) {
            Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
            for (FollowingDO record : records) {
                String member = String.valueOf(record.getUserId());
                double score = DateUtils.localDateTime2Timestamp(record.getCreateTime());
                tuples.add(ZSetOperations.TypedTuple.of(member, score));
            }
            stringRedisTemplate.opsForZSet().add(key, tuples);
        }

        // 3. 设置过期时间
        stringRedisTemplate.expire(key, ttlSeconds(), TimeUnit.SECONDS);
    }

    private List<String> range(String key, long offset, int count) {
        if (count <= 0) {
            return Collections.emptyList();
        }
        Set<String> members = stringRedisTemplate.opsForZSet().reverseRange(key, offset, offset + count - 1L);
        if (CollUtil.isEmpty(members)) {
            return Collections.emptyList();
        }
        return members.stream()
                .filter(m -> !"-1".equals(m))
                .toList();
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
        if (CollUtil.isEmpty(rows)) {
            return Collections.emptyList();
        }
        int from = (int) Math.min(offset, Integer.MAX_VALUE);
        int to = from + count;
        return CollUtil.sub(rows, from, to).stream()
                .map(mapper)
                .map(String::valueOf)
                .toList();
    }

}
