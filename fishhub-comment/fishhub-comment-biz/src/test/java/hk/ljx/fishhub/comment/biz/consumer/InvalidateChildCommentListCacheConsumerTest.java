package hk.ljx.fishhub.comment.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.comment.biz.model.dto.InvalidateChildCommentListCacheMqDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InvalidateChildCommentListCacheConsumerTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @InjectMocks
    private InvalidateChildCommentListCacheConsumer consumer;

    @Test
    void shouldEvictParentChildList() {
        Long parentCommentId = 100L;

        consumer.onMessage(JsonUtils.toJsonString(InvalidateChildCommentListCacheMqDTO.builder()
                .eventId("event-100")
                .parentCommentId(parentCommentId)
                .build()));

        verify(redisTemplate).delete(RedisKeyConstants.buildChildCommentListKey(parentCommentId));
    }
}
