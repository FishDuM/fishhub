package hk.ljx.fishhub.comment.biz.service.impl;

import hk.ljx.fishhub.comment.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO;
import hk.ljx.framework.common.util.SafeRedisUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentCacheServiceImplTest {

    @Mock
    private SafeRedisUtil safeRedisUtil;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock rebuildLock;
    @InjectMocks
    private CommentCacheServiceImpl cacheService;

    @Test
    void multiGetCommentDetailsShouldReturnEmptyListWhenKeysEmpty() {
        var result = cacheService.multiGetCommentDetails(Collections.emptyList());
        assertTrue(result.isEmpty());
        verify(safeRedisUtil, never()).multiGet(any());
    }

    @Test
    void batchGetCommentCountsShouldReturnEmptyMapWhenIdsEmpty() {
        var result = cacheService.batchGetCommentCounts(Collections.emptyList());
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnCachedTotalWhenAvailable() {
        Long noteId = 100L;
        String cacheKey = RedisKeyConstants.buildOneLevelCommentTotalCacheKey(noteId);

        when(safeRedisUtil.get(cacheKey)).thenReturn("42");

        long total = cacheService.getOneLevelCommentTotal(noteId, () -> 999L);

        assertEquals(42L, total);
        verify(redissonClient, never()).getLock(any());
    }

    @Test
    void shouldDoubleCheckAndLoadFromDbSupplierWhenCacheMiss() throws InterruptedException {
        Long noteId = 100L;
        String cacheKey = RedisKeyConstants.buildOneLevelCommentTotalCacheKey(noteId);
        String lockKey = RedisKeyConstants.buildOneLevelCommentTotalCacheLockKey(noteId);

        when(safeRedisUtil.get(cacheKey)).thenReturn(null, (String) null);
        when(redissonClient.getLock(lockKey)).thenReturn(rebuildLock);
        when(rebuildLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rebuildLock.isHeldByCurrentThread()).thenReturn(true);

        long total = cacheService.getOneLevelCommentTotal(noteId, () -> 88L);

        assertEquals(88L, total);
        verify(safeRedisUtil).set(eq(cacheKey), eq("88"), anyLong(), eq(TimeUnit.SECONDS));
        verify(rebuildLock).unlock();
    }

    @Test
    void tryLockCommentListRebuildShouldReturnTrueWhenAcquired() throws InterruptedException {
        Long noteId = 100L;
        String lockKey = RedisKeyConstants.buildCommentListRebuildLockKey(noteId);
        when(redissonClient.getLock(lockKey)).thenReturn(rebuildLock);
        when(rebuildLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);

        assertTrue(cacheService.tryLockCommentListRebuild(noteId));
    }

    @Test
    void tryLockCommentListRebuildShouldReturnFalseWhenLockNotAcquired() throws InterruptedException {
        Long noteId = 100L;
        String lockKey = RedisKeyConstants.buildCommentListRebuildLockKey(noteId);
        when(redissonClient.getLock(lockKey)).thenReturn(rebuildLock);
        when(rebuildLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(false);

        assertFalse(cacheService.tryLockCommentListRebuild(noteId));
    }

    @Test
    void syncHeatCommentsShouldNoopWhenEmpty() {
        cacheService.syncHeatComments(100L, Collections.emptyList());
        verify(stringRedisTemplate, never()).executePipelined(any(org.springframework.data.redis.core.SessionCallback.class));
    }

    @Test
    void syncChildCommentsShouldNoopWhenEmpty() {
        cacheService.syncChildComments(200L, Collections.emptyList());
        verify(stringRedisTemplate, never()).executePipelined(any(org.springframework.data.redis.core.SessionCallback.class));
    }
}
