package hk.ljx.fishhub.note.biz.service;

import hk.ljx.fishhub.note.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteCollectionDO;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteLikeDO;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteCollectionDOMapper;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteLikeDOMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteInteractionCacheServiceTest {

    private static final String LIKE_KEY = RedisKeyConstants.buildUserNoteLikeSetKey(1L);
    private static final String LOCK_KEY = RedisKeyConstants.buildUserNoteInteractionInitLockKey(1L);

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private NoteLikeDOMapper noteLikeDOMapper;
    @Mock
    private NoteCollectionDOMapper noteCollectionDOMapper;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock rebuildLock;
    @Mock
    private SetOperations<String, String> setOperations;

    @InjectMocks
    private NoteInteractionCacheService service;

    @Test
    void shouldRebuildLikeCacheOnlyByLockWinner() throws InterruptedException {
        when(stringRedisTemplate.hasKey(LIKE_KEY)).thenReturn(false, false);
        when(redissonClient.getLock(LOCK_KEY)).thenReturn(rebuildLock);
        when(rebuildLock.tryLock(0, 2L, TimeUnit.SECONDS)).thenReturn(true);
        when(rebuildLock.isHeldByCurrentThread()).thenReturn(true);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember(LIKE_KEY, "5")).thenReturn(true);
        when(noteLikeDOMapper.selectByUserId(1L)).thenReturn(List.of(
                NoteLikeDO.builder().noteId(5L).build(),
                NoteLikeDO.builder().noteId(6L).build()));

        boolean liked = service.isLiked(1L, 5L);

        org.junit.jupiter.api.Assertions.assertTrue(liked);
        verify(noteLikeDOMapper, times(1)).selectByUserId(1L);
        // 初始化 Set 写入（__initialized__ 成员 + 过期时间）
        verify(setOperations).add(LIKE_KEY, "__initialized__");
        verify(stringRedisTemplate).expire(eq(LIKE_KEY), anyLong(), any(TimeUnit.class));
        verify(rebuildLock).unlock();
    }

    @Test
    void shouldWaitThenFallbackBuildWhenLockNotAcquired() throws InterruptedException {
        when(stringRedisTemplate.hasKey(LIKE_KEY)).thenReturn(false, false, false, false);
        when(redissonClient.getLock(LOCK_KEY)).thenReturn(rebuildLock);
        when(rebuildLock.tryLock(0, 2L, TimeUnit.SECONDS)).thenReturn(false);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember(LIKE_KEY, "5")).thenReturn(true);
        when(noteLikeDOMapper.selectByUserId(1L)).thenReturn(List.of(
                NoteLikeDO.builder().noteId(5L).build()));

        boolean liked = service.isLiked(1L, 5L);

        org.junit.jupiter.api.Assertions.assertTrue(liked);
        verify(noteLikeDOMapper, times(1)).selectByUserId(1L);
        verify(rebuildLock, never()).unlock();
    }

    @Test
    void shouldSkipDbWhenCacheAppearedDuringLockWait() throws InterruptedException {
        when(stringRedisTemplate.hasKey(LIKE_KEY)).thenReturn(false, true);
        when(redissonClient.getLock(LOCK_KEY)).thenReturn(rebuildLock);
        when(rebuildLock.tryLock(0, 2L, TimeUnit.SECONDS)).thenReturn(true);
        when(rebuildLock.isHeldByCurrentThread()).thenReturn(true);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember(LIKE_KEY, "5")).thenReturn(true);

        boolean liked = service.isLiked(1L, 5L);

        org.junit.jupiter.api.Assertions.assertTrue(liked);
        verify(noteLikeDOMapper, never()).selectByUserId(anyLong());
        verify(rebuildLock).unlock();
    }
}
