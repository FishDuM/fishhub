package hk.ljx.fishhub.comment.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.cache.CommentDetailCache;
import hk.ljx.fishhub.comment.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.comment.biz.model.dto.InvalidateCommentCacheMqDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InvalidateCommentCacheConsumerTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private CommentDetailCache commentDetailCache;
    @InjectMocks
    private InvalidateCommentCacheConsumer consumer;

    @Test
    void shouldEvictDeletedAndParentCommentCaches() {
        consumer.onMessage(JsonUtils.toJsonString(InvalidateCommentCacheMqDTO.builder()
                .eventId("event-100")
                .deletedCommentIds(List.of(101L, 102L))
                .parentCommentId(100L)
                .build()));

        verify(redisTemplate).delete(RedisKeyConstants.buildHaveFirstReplyCommentKey(100L));
        verify(redisTemplate).delete(List.of(
                RedisKeyConstants.buildCountCommentKey(101L),
                RedisKeyConstants.buildCountCommentKey(102L),
                RedisKeyConstants.buildCountCommentKey(100L)));
        verify(commentDetailCache).delete(List.of(
                RedisKeyConstants.buildCommentDetailKey(101L),
                RedisKeyConstants.buildCommentDetailKey(102L),
                RedisKeyConstants.buildCommentDetailKey(100L)));
    }
}
