package hk.ljx.fishhub.comment.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import hk.ljx.fishhub.comment.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO;
import hk.ljx.fishhub.comment.biz.service.CommentCacheService;
import hk.ljx.fishhub.comment.biz.service.CommentChangedLocalHandler;
import hk.ljx.fishhub.count.constant.CountKeyConstants;
import hk.ljx.framework.common.util.CacheTtl;
import hk.ljx.framework.common.util.DateUtils;
import hk.ljx.framework.common.util.SafeRedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentCacheServiceImpl implements CommentCacheService {

    private final SafeRedisUtil safeRedisUtil;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    // 1. 评论详情缓存

    @Override
    public List<String> multiGetCommentDetails(List<String> keys) {
        if (CollUtil.isEmpty(keys)) {
            return Collections.emptyList();
        }
        List<String> values = safeRedisUtil.multiGet(keys);
        return values == null ? Collections.nCopies(keys.size(), null) : values;
    }

    @Override
    public void batchPutCommentDetails(Map<String, String> data) {
        if (CollUtil.isEmpty(data)) {
            return;
        }
        try {
            stringRedisTemplate.executePipelined(new SessionCallback<>() {
                @Override
                public Object execute(RedisOperations operations) {
                    for (Map.Entry<String, String> entry : data.entrySet()) {
                        long expireSeconds = CacheTtl.hours(1, 4);
                        operations.opsForValue().set(entry.getKey(), entry.getValue(), expireSeconds, TimeUnit.SECONDS);
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            log.warn("批量写入评论详情缓存异常, count={}", data.size(), e);
        }
    }

    @Override
    public void evictCommentDetails(Collection<String> keys) {
        if (CollUtil.isNotEmpty(keys)) {
            safeRedisUtil.delete(keys);
        }
    }

    // 2. 评论计数缓存

    @Override
    public Map<Long, Map<String, String>> batchGetCommentCounts(List<Long> commentIds) {
        if (CollUtil.isEmpty(commentIds)) {
            return Collections.emptyMap();
        }
        List<String> keys = commentIds.stream()
                .map(CountKeyConstants::buildCountCommentKey)
                .toList();

        try {
            List<Object> results = stringRedisTemplate.executePipelined(new SessionCallback<>() {
                @Override
                public Object execute(RedisOperations operations) {
                    keys.forEach(k -> operations.opsForHash().entries(k));
                    return null;
                }
            });

            Map<Long, Map<String, String>> resultMap = new HashMap<>(commentIds.size());
            for (int i = 0; i < commentIds.size(); i++) {
                if (i < results.size() && results.get(i) instanceof Map<?, ?> rawMap) {
                    if (CollUtil.isNotEmpty(rawMap)) {
                        @SuppressWarnings("unchecked")
                        Map<String, String> hash = (Map<String, String>) rawMap;
                        resultMap.put(commentIds.get(i), hash);
                    }
                }
            }
            return resultMap;
        } catch (Exception e) {
            log.warn("批量读取评论计数缓存异常, count={}", commentIds.size(), e);
            return Collections.emptyMap();
        }
    }

    @Override
    public void batchPutCommentCounts(Map<Long, Map<String, String>> countData) {
        if (CollUtil.isEmpty(countData)) {
            return;
        }
        try {
            stringRedisTemplate.executePipelined(new SessionCallback<>() {
                @Override
                public Object execute(RedisOperations operations) {
                    countData.forEach((commentId, map) -> {
                        String key = CountKeyConstants.buildCountCommentKey(commentId);
                        operations.opsForHash().putAll(key, map);
                        operations.expire(key, CacheTtl.hours(1, 4), TimeUnit.SECONDS);
                    });
                    return null;
                }
            });
        } catch (Exception e) {
            log.warn("批量回填评论计数缓存异常, count={}", countData.size(), e);
        }
    }

    @Override
    public void putCommentCount(Long commentId, Long childTotal, Long likeTotal) {
        if (commentId == null) {
            return;
        }
        String key = CountKeyConstants.buildCountCommentKey(commentId);
        Map<String, String> map = new HashMap<>(2);
        if (childTotal != null) {
            map.put(CountKeyConstants.FIELD_CHILD_COMMENT_TOTAL, String.valueOf(childTotal));
        }
        if (likeTotal != null) {
            map.put(CountKeyConstants.FIELD_LIKE_TOTAL, String.valueOf(likeTotal));
        }
        safeRedisUtil.hPutAll(key, map);
        safeRedisUtil.expire(key, CacheTtl.hours(1, 4), TimeUnit.SECONDS);
    }

    @Override
    public Long getChildCommentTotal(Long parentCommentId) {
        if (parentCommentId == null) {
            return null;
        }
        String key = CountKeyConstants.buildCountCommentKey(parentCommentId);
        Object val = safeRedisUtil.hGet(key, CountKeyConstants.FIELD_CHILD_COMMENT_TOTAL);
        if (val != null && StringUtils.isNumeric(String.valueOf(val))) {
            return Long.parseLong(String.valueOf(val));
        }
        return null;
    }

    // 3. 一级评论分页总数缓存防击穿

    @Override
    public long getOneLevelCommentTotal(Long noteId, Supplier<Long> dbLoader) {
        if (noteId == null) {
            return 0L;
        }
        String version = readOneLevelCommentTotalVersion(noteId);
        String key = RedisKeyConstants.buildOneLevelCommentTotalCacheKey(noteId, version);
        String lockKey = RedisKeyConstants.buildOneLevelCommentTotalCacheLockKey(noteId);

        String cached = safeRedisUtil.get(key);
        if (cached != null && StringUtils.isNumeric(cached)) {
            return Long.parseLong(cached);
        }

        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(3000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("获取一级评论数重建锁异常, lockKey={}", lockKey, e);
        }

        if (acquired) {
            try {
                cached = safeRedisUtil.get(key);
                if (cached != null && StringUtils.isNumeric(cached)) {
                    return Long.parseLong(cached);
                }
                long total = Objects.requireNonNullElse(dbLoader.get(), 0L);
                safeRedisUtil.set(key, String.valueOf(total), CacheTtl.minutes(10, 5), TimeUnit.SECONDS);
                return total;
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }

        cached = safeRedisUtil.get(key);
        return (cached != null && StringUtils.isNumeric(cached)) ? Long.parseLong(cached) : Objects.requireNonNullElse(dbLoader.get(), 0L);
    }

    @Override
    public void invalidateOneLevelCommentTotal(Long noteId) {
        if (noteId != null) {
            String versionKey = RedisKeyConstants.buildOneLevelCommentTotalCacheVersionKey(noteId);
            safeRedisUtil.set(versionKey, String.valueOf(System.currentTimeMillis()),
                    RedisKeyConstants.ONE_LEVEL_COMMENT_TOTAL_CACHE_VERSION_EXPIRE_SECONDS, TimeUnit.SECONDS);
        }
    }

    private String readOneLevelCommentTotalVersion(Long noteId) {
        String versionKey = RedisKeyConstants.buildOneLevelCommentTotalCacheVersionKey(noteId);
        String version = safeRedisUtil.get(versionKey);
        if (version != null) {
            safeRedisUtil.expire(versionKey, RedisKeyConstants.ONE_LEVEL_COMMENT_TOTAL_CACHE_VERSION_EXPIRE_SECONDS, TimeUnit.SECONDS);
            return version;
        }
        return "0";
    }

    // 4. 评论列表/热度列表 ZSet

    @Override
    public boolean hasCommentListZSet(Long noteId) {
        return safeRedisUtil.hasKey(RedisKeyConstants.buildCommentListKey(noteId));
    }

    @Override
    public Set<String> getCommentIdsByZSet(Long noteId, long offset, long limit) {
        String key = RedisKeyConstants.buildCommentListKey(noteId);
        return safeRedisUtil.zReverseRange(key, offset, offset + limit - 1);
    }

    @Override
    public void syncHeatComments(Long noteId, List<CommentDO> heatComments) {
        if (CollUtil.isEmpty(heatComments)) {
            return;
        }
        String key = RedisKeyConstants.buildCommentListKey(noteId);
        try {
            stringRedisTemplate.executePipelined(new SessionCallback<>() {
                @Override
                public Object execute(RedisOperations operations) {
                    for (CommentDO commentDO : heatComments) {
                        Long commentId = commentDO.getId();
                        Double commentHeat = commentDO.getHeat();
                        operations.opsForZSet().add(key, String.valueOf(commentId), commentHeat != null ? commentHeat : 0.0);
                    }
                    long randomExpiryTime = CacheTtl.hours(1, 4);
                    operations.expire(key, randomExpiryTime, TimeUnit.SECONDS);
                    return null;
                }
            });
        } catch (Exception e) {
            log.warn("同步热点评论至 Redis 异常, noteId={}, count={}", noteId, heatComments.size(), e);
        }
    }

    @Override
    public boolean tryLockCommentListRebuild(Long noteId) {
        String lockKey = RedisKeyConstants.buildCommentListRebuildLockKey(noteId);
        RLock lock = redissonClient.getLock(lockKey);
        try {
            return lock.tryLock(2000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.warn("获取评论分页 ZSet 重建单飞锁异常, lockKey={}", lockKey, e);
            return false;
        }
    }

    @Override
    public void unlockCommentListRebuild(Long noteId) {
        String lockKey = RedisKeyConstants.buildCommentListRebuildLockKey(noteId);
        RLock lock = redissonClient.getLock(lockKey);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    // 5. 子评论列表 ZSet

    @Override
    public boolean hasChildCommentListZSet(Long parentCommentId) {
        return safeRedisUtil.hasKey(RedisKeyConstants.buildChildCommentListKey(parentCommentId));
    }

    @Override
    public Long getChildCommentZSetCard(Long parentCommentId) {
        return safeRedisUtil.zCard(RedisKeyConstants.buildChildCommentListKey(parentCommentId));
    }

    @Override
    public Set<String> getChildCommentIdsByZSet(Long parentCommentId, long offset, long limit) {
        String key = RedisKeyConstants.buildChildCommentListKey(parentCommentId);
        return safeRedisUtil.zRange(key, offset, offset + limit - 1);
    }

    @Override
    public void syncChildComments(Long parentCommentId, List<CommentDO> childComments) {
        if (CollUtil.isEmpty(childComments)) {
            return;
        }
        String key = RedisKeyConstants.buildChildCommentListKey(parentCommentId);
        try {
            stringRedisTemplate.executePipelined(new SessionCallback<>() {
                @Override
                public Object execute(RedisOperations operations) {
                    List<CommentDO> ascending = new ArrayList<>(childComments);
                    Collections.reverse(ascending);
                    for (CommentDO childCommentDO : ascending) {
                        Long commentId = childCommentDO.getId();
                        long commentTimestamp = DateUtils.localDateTime2Timestamp(childCommentDO.getCreateTime());
                        operations.opsForZSet().add(key, String.valueOf(commentId), commentTimestamp);
                    }
                    long randomExpiryTime = CacheTtl.hours(1, 4);
                    operations.expire(key, randomExpiryTime, TimeUnit.SECONDS);
                    return null;
                }
            });
        } catch (Exception e) {
            log.warn("同步子评论列表至 Redis 异常, parentCommentId={}, count={}", parentCommentId, childComments.size(), e);
        }
    }

    @Override
    public boolean tryLockChildCommentListRebuild(Long parentCommentId) {
        String lockKey = RedisKeyConstants.buildChildCommentListRebuildLockKey(parentCommentId);
        RLock lock = redissonClient.getLock(lockKey);
        try {
            return lock.tryLock(2000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.warn("获取子评论分页 ZSet 重建单飞锁异常, lockKey={}", lockKey, e);
            return false;
        }
    }

    @Override
    public void unlockChildCommentListRebuild(Long parentCommentId) {
        String lockKey = RedisKeyConstants.buildChildCommentListRebuildLockKey(parentCommentId);
        RLock lock = redissonClient.getLock(lockKey);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
