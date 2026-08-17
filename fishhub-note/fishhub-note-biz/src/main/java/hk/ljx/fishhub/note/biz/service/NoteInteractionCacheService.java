package hk.ljx.fishhub.note.biz.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import hk.ljx.fishhub.note.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteCollectionDO;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteLikeDO;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteCollectionDOMapper;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteLikeDOMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 用户笔记互动状态缓存。MySQL 关系表是数据源，Redis Set 只负责加速查询。
 */
@Slf4j
@Service
public class NoteInteractionCacheService {

    private static final String INITIALIZED_MEMBER = "__initialized__";
    private static final long BASE_EXPIRE_SECONDS = 24 * 60 * 60L;

    private static final int CACHE_REBUILD_RETRY_TIMES = 3;
    private static final long CACHE_REBUILD_RETRY_INTERVAL_MILLIS = 20L;
    private static final long INTERACTION_CACHE_REBUILD_LOCK_SECONDS = 2L;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private NoteLikeDOMapper noteLikeDOMapper;
    @Resource
    private NoteCollectionDOMapper noteCollectionDOMapper;
    @Resource
    private RedissonClient redissonClient;

    public boolean isLiked(Long userId, Long noteId) {
        String key = ensureLikeCache(userId);
        return Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(key, String.valueOf(noteId)));
    }

    public boolean addLike(Long userId, Long noteId) {
        String key = ensureLikeCache(userId);
        Long added = stringRedisTemplate.opsForSet().add(key, String.valueOf(noteId));
        return added != null && added > 0;
    }

    public boolean removeLike(Long userId, Long noteId) {
        String key = ensureLikeCache(userId);
        Long removed = stringRedisTemplate.opsForSet().remove(key, String.valueOf(noteId));
        return removed != null && removed > 0;
    }

    public boolean isCollected(Long userId, Long noteId) {
        String key = ensureCollectCache(userId);
        return Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(key, String.valueOf(noteId)));
    }

    public boolean addCollect(Long userId, Long noteId) {
        String key = ensureCollectCache(userId);
        Long added = stringRedisTemplate.opsForSet().add(key, String.valueOf(noteId));
        return added != null && added > 0;
    }

    public boolean removeCollect(Long userId, Long noteId) {
        String key = ensureCollectCache(userId);
        Long removed = stringRedisTemplate.opsForSet().remove(key, String.valueOf(noteId));
        return removed != null && removed > 0;
    }

    public Set<Long> findLikedNoteIds(Long userId, Collection<Long> noteIds) {
        if (CollUtil.isEmpty(noteIds)) {
            return Collections.emptySet();
        }
        String key = ensureLikeCache(userId);
        Set<Long> likedNoteIds = new HashSet<>();
        for (Long noteId : noteIds) {
            if (Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(key, String.valueOf(noteId)))) {
                likedNoteIds.add(noteId);
            }
        }
        return likedNoteIds;
    }

    public void evictLikeCache(Long userId) {
        stringRedisTemplate.delete(RedisKeyConstants.buildUserNoteLikeSetKey(userId));
    }

    /**
     * 异步消费者拒绝乐观点赞时清空该用户的相关读缓存；下次刷新从 MySQL 重建真实状态。
     */
    public void evictLikeCaches(Long userId) {
        evictLikeCache(userId);
        stringRedisTemplate.delete(RedisKeyConstants.buildUserNoteLikeZSetKey(userId));
    }

    public void evictCollectCache(Long userId) {
        stringRedisTemplate.delete(RedisKeyConstants.buildUserNoteCollectSetKey(userId));
    }

    /**
     * 异步消费者拒绝乐观收藏时清空该用户的相关读缓存；下次刷新从 MySQL 重建真实状态。
     */
    public void evictCollectCaches(Long userId) {
        evictCollectCache(userId);
        stringRedisTemplate.delete(RedisKeyConstants.buildUserNoteCollectZSetKey(userId));
    }

    private String ensureLikeCache(Long userId) {
        String key = RedisKeyConstants.buildUserNoteLikeSetKey(userId);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            return key;
        }
        String lockKey = RedisKeyConstants.buildUserNoteInteractionInitLockKey(userId);
        RLock lock = tryAcquireRebuildLock(lockKey, INTERACTION_CACHE_REBUILD_LOCK_SECONDS);
        if (lock == null) {
            // 抢不到锁则轮询等待，超时本地兜底重建。
            if (waitForCacheKey(key)) {
                return key;
            }
            List<NoteLikeDO> records = noteLikeDOMapper.selectByUserId(userId);
            initializeSet(key, records == null ? Collections.emptyList() : records.stream()
                    .map(NoteLikeDO::getNoteId)
                    .toList());
            return key;
        }
        try {
            // 二次检查：抢锁期间其他节点可能已写入。
            if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                List<NoteLikeDO> records = noteLikeDOMapper.selectByUserId(userId);
                initializeSet(key, records == null ? Collections.emptyList() : records.stream()
                        .map(NoteLikeDO::getNoteId)
                        .toList());
            }
            return key;
        } finally {
            releaseRebuildLock(lock, lockKey);
        }
    }

    private String ensureCollectCache(Long userId) {
        String key = RedisKeyConstants.buildUserNoteCollectSetKey(userId);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            return key;
        }
        String lockKey = RedisKeyConstants.buildUserNoteInteractionInitLockKey(userId);
        RLock lock = tryAcquireRebuildLock(lockKey, INTERACTION_CACHE_REBUILD_LOCK_SECONDS);
        if (lock == null) {
            if (waitForCacheKey(key)) {
                return key;
            }
            List<NoteCollectionDO> records = noteCollectionDOMapper.selectByUserId(userId);
            initializeSet(key, records == null ? Collections.emptyList() : records.stream()
                    .map(NoteCollectionDO::getNoteId)
                    .toList());
            return key;
        }
        try {
            if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                List<NoteCollectionDO> records = noteCollectionDOMapper.selectByUserId(userId);
                initializeSet(key, records == null ? Collections.emptyList() : records.stream()
                        .map(NoteCollectionDO::getNoteId)
                        .toList());
            }
            return key;
        } finally {
            releaseRebuildLock(lock, lockKey);
        }
    }

    private boolean waitForCacheKey(String key) {
        for (int i = 0; i < CACHE_REBUILD_RETRY_TIMES; i++) {
            sleepBeforeCacheRetry();
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                return true;
            }
        }
        return false;
    }

    private RLock tryAcquireRebuildLock(String lockKey, long leaseSeconds) {
        RLock lock = redissonClient.getLock(lockKey);
        if (lock == null) {
            return null;
        }
        try {
            return lock.tryLock(0, leaseSeconds, TimeUnit.SECONDS) ? lock : null;
        } catch (Exception e) {
            throw new IllegalStateException("Redis 不可用，互动缓存重建锁获取失败, lockKey=" + lockKey, e);
        }
    }

    private void releaseRebuildLock(RLock lock, String lockKey) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (Exception e) {
            log.warn("Redis 不可用，互动缓存重建锁释放失败，key={}", lockKey, e);
        }
    }

    private void sleepBeforeCacheRetry() {
        try {
            Thread.sleep(CACHE_REBUILD_RETRY_INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void initializeSet(String key, Collection<Long> noteIds) {
        stringRedisTemplate.opsForSet().add(key, INITIALIZED_MEMBER);
        if (CollUtil.isNotEmpty(noteIds)) {
            String[] members = noteIds.stream().map(String::valueOf).toArray(String[]::new);
            stringRedisTemplate.opsForSet().add(key, members);
        }
        stringRedisTemplate.expire(key, BASE_EXPIRE_SECONDS + RandomUtil.randomLong(BASE_EXPIRE_SECONDS), TimeUnit.SECONDS);
    }
}
