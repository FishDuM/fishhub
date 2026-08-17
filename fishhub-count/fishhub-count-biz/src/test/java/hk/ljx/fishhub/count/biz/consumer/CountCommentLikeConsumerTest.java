package hk.ljx.fishhub.count.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.count.biz.constant.MQConstants;
import hk.ljx.fishhub.count.biz.model.dto.AggregationCountLikeUnlikeCommentMqDTO;
import hk.ljx.fishhub.count.biz.model.dto.CountLikeUnlikeCommentMqDTO;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CountCommentLikeConsumerTest {

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    @InjectMocks
    private CountCommentLikeConsumer consumer;

    @Test
    void shouldAggregateWholeBatchAndSendOnlyOneMessage() throws Exception {
        List<String> bodys = List.of(
                body(10L, 1),  // 评论10 点赞 +1
                body(10L, 0),  // 评论10 取消 -1 → 合并为 0
                body(11L, 1)   // 评论11 点赞 +1
        );

        ReflectionTestUtils.invokeMethod(consumer, "consumeMessage", bodys);

        ArgumentCaptor<Message<?>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        // 整批聚合后只发 1 条消息给 2DB
        verify(rocketMQTemplate, times(1)).syncSend(eq(MQConstants.TOPIC_COUNT_COMMENT_LIKE_2_DB), messageCaptor.capture());
        List<AggregationCountLikeUnlikeCommentMqDTO> list = JsonUtils.parseList(
                String.valueOf(messageCaptor.getValue().getPayload()), AggregationCountLikeUnlikeCommentMqDTO.class);
        assertEquals(2, list.size());
        Map<Long, Integer> byComment = list.stream()
                .collect(Collectors.toMap(AggregationCountLikeUnlikeCommentMqDTO::getCommentId, AggregationCountLikeUnlikeCommentMqDTO::getCount));
        assertEquals(0, byComment.get(10L));
        assertEquals(1, byComment.get(11L));
    }

    private String body(long commentId, int type) {
        return JsonUtils.toJsonString(CountLikeUnlikeCommentMqDTO.builder()
                .commentId(commentId)
                .type(type)
                .build());
    }
}
