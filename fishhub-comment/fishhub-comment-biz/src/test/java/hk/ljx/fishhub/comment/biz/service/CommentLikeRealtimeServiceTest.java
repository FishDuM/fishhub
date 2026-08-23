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
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评论点赞实时服务测试
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
    private SetOperations<String, String> setOps;
    @Mock
    private ZSetOperations<String, String> zsetOps;
    @InjectMocks
    private CommentLikeRealtimeService service;

    @Test
    void shouldReturnTrueWhenWarmSetContainsMember() {
        String setKey = RedisKeyConstants.buildUserCommentLikeSetKey(2L);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.isMember(setKey, RedisKeyConstants.COMMENT_LIKE_SET_INITIALIZED)).thenReturn(true);
        when(setOps.isMember(setKey, "100")).thenReturn(true);

        assertTrue(service.containsLiked(2L, 100L));
    }

    @Test
    void shouldReturnFalseWhenWarmSetMissesMember() {
        String setKey = RedisKeyConstants.buildUserCommentLikeSetKey(2L);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.isMember(setKey, RedisKeyConstants.COMMENT_LIKE_SET_INITIALIZED)).thenReturn(true);
        when(setOps.isMember(setKey, "100")).thenReturn(false);

        assertFalse(service.containsLiked(2L, 100L));
    }

    @Test
    void shouldRebuildFromDatabaseThenAnswerWhenSetNotInitialized() {
        String setKey = RedisKeyConstants.buildUserCommentLikeSetKey(2L);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOps);
        lenient().when(stringRedisTemplate.opsForZSet()).thenReturn(zsetOps);
        lenient().when(stringRedisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(setOps.isMember(setKey, RedisKeyConstants.COMMENT_LIKE_SET_INITIALIZED)).thenReturn(false);
        when(commentLikeDOMapper.selectLikedCommentsByUserId(2L))
                .thenReturn(List.of(CommentLikeDO.builder()
                        .commentId(100L)
                        .createTime(LocalDateTime.now())
                        .build()));
        when(setOps.isMember(setKey, "100")).thenReturn(true);

        assertTrue(service.containsLiked(2L, 100L));
        verify(commentLikeDOMapper).selectLikedCommentsByUserId(2L);
    }
}
