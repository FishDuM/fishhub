package hk.ljx.fishhub.note.biz.service;

import hk.ljx.fishhub.note.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteLikeDO;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteCollectionDOMapper;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteLikeDOMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteInteractionCacheServiceTest {

    private static final String LIKE_KEY = RedisKeyConstants.buildUserNoteLikeZSetKey(1L);

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private NoteLikeDOMapper noteLikeDOMapper;
    @Mock
    private NoteCollectionDOMapper noteCollectionDOMapper;
    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @InjectMocks
    private NoteInteractionCacheService service;

    @Test
    void shouldRebuildLikeCacheWhenMissing() {
        when(stringRedisTemplate.hasKey(LIKE_KEY)).thenReturn(false);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.score(LIKE_KEY, "5")).thenReturn(1700000000.0);
        when(noteLikeDOMapper.selectLikedByUserIdAndLimit(1L, NoteInteractionCacheService.MAX_INTERACTION_ZSET_SIZE)).thenReturn(List.of(
                NoteLikeDO.builder().noteId(5L).createTime(LocalDateTime.now()).build(),
                NoteLikeDO.builder().noteId(6L).createTime(LocalDateTime.now()).build()));

        boolean liked = service.isLiked(1L, 5L);

        org.junit.jupiter.api.Assertions.assertTrue(liked);
        verify(noteLikeDOMapper, times(1)).selectLikedByUserIdAndLimit(1L, NoteInteractionCacheService.MAX_INTERACTION_ZSET_SIZE);
        verify(zSetOperations).add(LIKE_KEY, "__empty__", 0.0);
        verify(stringRedisTemplate).expire(eq(LIKE_KEY), anyLong(), any(TimeUnit.class));
    }

    @Test
    void shouldReadDirectlyFromCacheWhenPresent() {
        when(stringRedisTemplate.hasKey(LIKE_KEY)).thenReturn(true);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.score(LIKE_KEY, "5")).thenReturn(1700000000.0);

        boolean liked = service.isLiked(1L, 5L);

        org.junit.jupiter.api.Assertions.assertTrue(liked);
        verify(noteLikeDOMapper, never()).selectLikedByUserIdAndLimit(anyLong(), any(Integer.class));
    }
}
