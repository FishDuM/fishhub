package hk.ljx.fishhub.comment.biz.service;

import hk.ljx.fishhub.comment.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentLikeDO;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentLikeDOMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评论点赞实时服务测试（纯 ZSet 测试）
 */
@ExtendWith(MockitoExtension.class)
class CommentLikeRealtimeServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private CommentDOMapper commentDOMapper;
    @Mock
    private CommentLikeDOMapper commentLikeDOMapper;
    @Mock
    private ZSetOperations<String, String> zsetOps;
    @InjectMocks
    private CommentLikeRealtimeService service;

    @Test
    void shouldReturnTrueWhenWarmZSetContainsMember() {
        String zsetKey = RedisKeyConstants.buildUserCommentLikeZSetKey(2L);
        when(stringRedisTemplate.hasKey(zsetKey)).thenReturn(true);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zsetOps);
        when(zsetOps.score(zsetKey, "100")).thenReturn(123456789.0);

        assertTrue(service.containsLiked(2L, 100L));
    }

    @Test
    void shouldReturnFalseWhenWarmZSetMissesMember() {
        String zsetKey = RedisKeyConstants.buildUserCommentLikeZSetKey(2L);
        when(stringRedisTemplate.hasKey(zsetKey)).thenReturn(true);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zsetOps);
        when(zsetOps.score(zsetKey, "100")).thenReturn(null);

        assertFalse(service.containsLiked(2L, 100L));
    }

    @Test
    void shouldRebuildFromDatabaseThenAnswerWhenZSetNotInitialized() {
        String zsetKey = RedisKeyConstants.buildUserCommentLikeZSetKey(2L);
        when(stringRedisTemplate.hasKey(zsetKey)).thenReturn(false);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zsetOps);
        when(commentLikeDOMapper.selectLikedCommentsByUserId(2L))
                .thenReturn(List.of(CommentLikeDO.builder()
                        .commentId(100L)
                        .createTime(LocalDateTime.now())
                        .build()));
        when(zsetOps.score(zsetKey, "100")).thenReturn(123456789.0);

        assertTrue(service.containsLiked(2L, 100L));
        verify(commentLikeDOMapper).selectLikedCommentsByUserId(2L);
    }
}
