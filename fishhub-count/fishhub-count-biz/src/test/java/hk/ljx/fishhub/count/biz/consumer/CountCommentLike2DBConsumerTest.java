package hk.ljx.fishhub.count.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.count.biz.constant.MQConstants;
import hk.ljx.fishhub.count.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.count.biz.model.dto.AggregationCountLikeUnlikeCommentMqDTO;
import hk.ljx.fishhub.count.biz.service.MqIdempotentExecutor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CountCommentLike2DBConsumerTest {

    @Mock
    private CommentDOMapper commentDOMapper;
    @Mock
    private MqIdempotentExecutor mqIdempotentExecutor;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private RocketMQTemplate rocketMQTemplate;
    @InjectMocks
    private CountCommentLike2DBConsumer consumer;

    @Test
    void shouldRequestHeatRebuildWhenRetryFindsMessageAlreadyApplied() throws Exception {
        when(mqIdempotentExecutor.execute(anyString(), anyString(), any())).thenReturn(false);
        String body = JsonUtils.toJsonString(List.of(
                AggregationCountLikeUnlikeCommentMqDTO.builder()
                        .commentId(10L)
                        .count(5)
                        .batchId("batch-10")
                        .build()));

        consumer.onMessage(body);

        ArgumentCaptor<Message<?>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rocketMQTemplate).syncSend(eq(MQConstants.TOPIC_COMMENT_HEAT_UPDATE), messageCaptor.capture());
        Message<?> message = (Message<?>) messageCaptor.getValue();
        assertEquals(Set.of(10L), JsonUtils.parseSet(
                String.valueOf(message.getPayload()), Long.class));
    }
}
