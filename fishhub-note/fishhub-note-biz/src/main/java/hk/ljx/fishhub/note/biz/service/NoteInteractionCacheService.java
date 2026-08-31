package hk.ljx.fishhub.note.biz.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import hk.ljx.fishhub.note.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteCollectionDO;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteLikeDO;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteCollectionDOMapper;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteLikeDOMapper;
import hk.ljx.framework.common.util.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 用户笔记互动状态缓存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoteInteractionCacheService {

    private static final String INITIALIZED_MEMBER = "__empty__";
    private static final long BASE_EXPIRE_SECONDS = 24 * 60 * 60L;
    public static final int MAX_INTERACTION_ZSET_SIZE = 1000;

    private final StringRedisTemplate stringRedisTemplate;
    private final NoteLikeDOMapper noteLikeDOMapper;
    private final NoteCollectionDOMapper noteCollectionDOMapper;

    public boolean isLiked(Long userId, Long noteId) {
        String key = ensureLikeCache(userId);
        Double score = stringRedisTemplate.opsForZSet().score(key, String.valueOf(noteId));
        return score != null;
    }

    public boolean addLike(Long userId, Long noteId) {
        String key = ensureLikeCache(userId);
        long now = System.currentTimeMillis();
        Boolean added = stringRedisTemplate.opsForZSet().add(key, String.valueOf(noteId), (double) now);
        trimZSetCapacity(key);
        return Boolean.TRUE.equals(added);
    }

    public boolean removeLike(Long userId, Long noteId) {
        String key = ensureLikeCache(userId);
        Long removed = stringRedisTemplate.opsForZSet().remove(key, String.valueOf(noteId));
        return removed != null && removed > 0;
    }

    public boolean isCollected(Long userId, Long noteId) {
        String key = ensureCollectCache(userId);
        Double score = stringRedisTemplate.opsForZSet().score(key, String.valueOf(noteId));
        return score != null;
    }

    public boolean addCollect(Long userId, Long noteId) {
        String key = ensureCollectCache(userId);
        long now = System.currentTimeMillis();
        Boolean added = stringRedisTemplate.opsForZSet().add(key, String.valueOf(noteId), (double) now);
        trimZSetCapacity(key);
        return Boolean.TRUE.equals(added);
    }

    public boolean removeCollect(Long userId, Long noteId) {
        String key = ensureCollectCache(userId);
        Long removed = stringRedisTemplate.opsForZSet().remove(key, String.valueOf(noteId));
        return removed != null && removed > 0;
    }

    public Set<Long> findLikedNoteIds(Long userId, Collection<Long> noteIds) {
        if (CollUtil.isEmpty(noteIds)) {
            return Collections.emptySet();
        }
        String key = ensureLikeCache(userId);
        List<Long> noteIdList = noteIds instanceof List<Long> list ? list : List.copyOf(noteIds);
        
        List<Object> pipelineResults = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            byte[] rawKey = stringRedisTemplate.getStringSerializer().serialize(key);
            for (Long noteId : noteIdList) {
                byte[] rawMember = stringRedisTemplate.getStringSerializer().serialize(String.valueOf(noteId));
                connection.zScore(rawKey, rawMember);
            }
            return null;
        });

        Set<Long> liked = new HashSet<>();
        for (int i = 0; i < noteIdList.size(); i++) {
            if (i < pipelineResults.size() && pipelineResults.get(i) != null) {
                liked.add(noteIdList.get(i));
            }
        }
        return liked;
    }

    public void evictLikeCache(Long userId) {
        stringRedisTemplate.delete(RedisKeyConstants.buildUserNoteLikeZSetKey(userId));
    }

    public void evictLikeCaches(Long userId) {
        evictLikeCache(userId);
    }

    public void evictCollectCache(Long userId) {
        stringRedisTemplate.delete(RedisKeyConstants.buildUserNoteCollectZSetKey(userId));
    }

    public void evictCollectCaches(Long userId) {
        evictCollectCache(userId);
    }

    private String ensureLikeCache(Long userId) {
        String key = RedisKeyConstants.buildUserNoteLikeZSetKey(userId);
        return ensureInteractionCache(key, () -> {
            List<NoteLikeDO> records = noteLikeDOMapper.selectLikedByUserIdAndLimit(userId, MAX_INTERACTION_ZSET_SIZE);
            return buildLikeTuples(records);
        });
    }

    private String ensureCollectCache(Long userId) {
        String key = RedisKeyConstants.buildUserNoteCollectZSetKey(userId);
        return ensureInteractionCache(key, () -> {
            List<NoteCollectionDO> records = noteCollectionDOMapper.selectCollectedByUserIdAndLimit(userId, MAX_INTERACTION_ZSET_SIZE);
            return buildCollectTuples(records);
        });
    }

    private Set<ZSetOperations.TypedTuple<String>> buildLikeTuples(List<NoteLikeDO> records) {
        if (CollUtil.isEmpty(records)) {
            return Collections.emptySet();
        }
        Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>(records.size());
        for (NoteLikeDO record : records) {
            if (record != null && record.getNoteId() != null) {
                double score = record.getCreateTime() != null ? DateUtils.localDateTime2Timestamp(record.getCreateTime()) : 0.0;
                tuples.add(new DefaultTypedTuple<>(String.valueOf(record.getNoteId()), score));
            }
        }
        return tuples;
    }

    private Set<ZSetOperations.TypedTuple<String>> buildCollectTuples(List<NoteCollectionDO> records) {
        if (CollUtil.isEmpty(records)) {
            return Collections.emptySet();
        }
        Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>(records.size());
        for (NoteCollectionDO record : records) {
            if (record != null && record.getNoteId() != null) {
                double score = record.getCreateTime() != null ? DateUtils.localDateTime2Timestamp(record.getCreateTime()) : 0.0;
                tuples.add(new DefaultTypedTuple<>(String.valueOf(record.getNoteId()), score));
            }
        }
        return tuples;
    }

    private String ensureInteractionCache(String key, Supplier<Set<ZSetOperations.TypedTuple<String>>> tupleSupplier) {
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            initializeZSet(key, tupleSupplier.get());
        }
        return key;
    }

    private void initializeZSet(String key, Set<ZSetOperations.TypedTuple<String>> tuples) {
        // 写入哨兵元素，防止空缓存击穿
        stringRedisTemplate.opsForZSet().add(key, INITIALIZED_MEMBER, 0.0);
        if (CollUtil.isNotEmpty(tuples)) {
            stringRedisTemplate.opsForZSet().add(key, tuples);
        }
        stringRedisTemplate.expire(key, BASE_EXPIRE_SECONDS + RandomUtil.randomLong(BASE_EXPIRE_SECONDS), TimeUnit.SECONDS);
    }

    private void trimZSetCapacity(String key) {
        try {
            Long size = stringRedisTemplate.opsForZSet().zCard(key);
            if (size != null && size > MAX_INTERACTION_ZSET_SIZE + 1) {
                // 淘汰超出容量限制的久远记录
                stringRedisTemplate.opsForZSet().removeRange(key, 1, size - MAX_INTERACTION_ZSET_SIZE - 1);
            }
        } catch (Exception e) {
            log.warn("互动缓存容量裁剪异常, key={}", key, e);
        }
    }
}
