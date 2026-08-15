package hk.ljx.fishhub.note.biz.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import hk.ljx.fishhub.note.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteCollectionDO;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteLikeDO;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteCollectionDOMapper;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteLikeDOMapper;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
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
@Service
public class NoteInteractionCacheService {

    private static final String INITIALIZED_MEMBER = "__initialized__";
    private static final long BASE_EXPIRE_SECONDS = 24 * 60 * 60L;

    @Resource
    private RedisTemplate<String, String> redisTemplate;
    @Resource
    private NoteLikeDOMapper noteLikeDOMapper;
    @Resource
    private NoteCollectionDOMapper noteCollectionDOMapper;

    public boolean isLiked(Long userId, Long noteId) {
        String key = ensureLikeCache(userId);
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, String.valueOf(noteId)));
    }

    public boolean addLike(Long userId, Long noteId) {
        String key = ensureLikeCache(userId);
        Long added = redisTemplate.opsForSet().add(key, String.valueOf(noteId));
        return added != null && added > 0;
    }

    public boolean removeLike(Long userId, Long noteId) {
        String key = ensureLikeCache(userId);
        Long removed = redisTemplate.opsForSet().remove(key, String.valueOf(noteId));
        return removed != null && removed > 0;
    }

    public boolean isCollected(Long userId, Long noteId) {
        String key = ensureCollectCache(userId);
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, String.valueOf(noteId)));
    }

    public boolean addCollect(Long userId, Long noteId) {
        String key = ensureCollectCache(userId);
        Long added = redisTemplate.opsForSet().add(key, String.valueOf(noteId));
        return added != null && added > 0;
    }

    public boolean removeCollect(Long userId, Long noteId) {
        String key = ensureCollectCache(userId);
        Long removed = redisTemplate.opsForSet().remove(key, String.valueOf(noteId));
        return removed != null && removed > 0;
    }

    public Set<Long> findLikedNoteIds(Long userId, Collection<Long> noteIds) {
        if (CollUtil.isEmpty(noteIds)) {
            return Collections.emptySet();
        }
        String key = ensureLikeCache(userId);
        Set<Long> likedNoteIds = new HashSet<>();
        for (Long noteId : noteIds) {
            if (Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, String.valueOf(noteId)))) {
                likedNoteIds.add(noteId);
            }
        }
        return likedNoteIds;
    }

    public void evictLikeCache(Long userId) {
        redisTemplate.delete(RedisKeyConstants.buildUserNoteLikeSetKey(userId));
    }

    /**
     * 异步消费者拒绝乐观点赞时清空该用户的相关读缓存；下次刷新从 MySQL 重建真实状态。
     */
    public void evictLikeCaches(Long userId) {
        evictLikeCache(userId);
        redisTemplate.delete(RedisKeyConstants.buildUserNoteLikeZSetKey(userId));
    }

    public void evictCollectCache(Long userId) {
        redisTemplate.delete(RedisKeyConstants.buildUserNoteCollectSetKey(userId));
    }

    /**
     * 异步消费者拒绝乐观收藏时清空该用户的相关读缓存；下次刷新从 MySQL 重建真实状态。
     */
    public void evictCollectCaches(Long userId) {
        evictCollectCache(userId);
        redisTemplate.delete(RedisKeyConstants.buildUserNoteCollectZSetKey(userId));
    }

    private String ensureLikeCache(Long userId) {
        String key = RedisKeyConstants.buildUserNoteLikeSetKey(userId);
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            List<NoteLikeDO> records = noteLikeDOMapper.selectByUserId(userId);
            initializeSet(key, records == null ? Collections.emptyList() : records.stream()
                    .map(NoteLikeDO::getNoteId)
                    .toList());
        }
        return key;
    }

    private String ensureCollectCache(Long userId) {
        String key = RedisKeyConstants.buildUserNoteCollectSetKey(userId);
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            List<NoteCollectionDO> records = noteCollectionDOMapper.selectByUserId(userId);
            initializeSet(key, records == null ? Collections.emptyList() : records.stream()
                    .map(NoteCollectionDO::getNoteId)
                    .toList());
        }
        return key;
    }

    private void initializeSet(String key, Collection<Long> noteIds) {
        redisTemplate.opsForSet().add(key, INITIALIZED_MEMBER);
        if (CollUtil.isNotEmpty(noteIds)) {
            String[] members = noteIds.stream().map(String::valueOf).toArray(String[]::new);
            redisTemplate.opsForSet().add(key, members);
        }
        redisTemplate.expire(key, BASE_EXPIRE_SECONDS + RandomUtil.randomLong(BASE_EXPIRE_SECONDS), TimeUnit.SECONDS);
    }
}
