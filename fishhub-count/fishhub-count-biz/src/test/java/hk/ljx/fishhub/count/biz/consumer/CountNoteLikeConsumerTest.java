package hk.ljx.fishhub.count.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.count.biz.constant.MQConstants;
import hk.ljx.fishhub.count.biz.model.dto.AggregationCountLikeUnlikeNoteMqDTO;
import hk.ljx.fishhub.count.biz.model.dto.CountLikeUnlikeNoteMqDTO;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * 链路贯通测试：点赞批量消费者发出的数组 payload（字段与 note 模块 LikeUnlikeNoteMqDTO 一致）
 * 必须能被计数聚合端正确解析并聚合出净 delta。
 */
@ExtendWith(MockitoExtension.class)
class CountNoteLikeConsumerTest {

    @Mock
    private RocketMQTemplate rocketMQTemplate;
    @InjectMocks
    private CountNoteLikeConsumer consumer;

    @Test
    void shouldAggregateArrayPayloadFromBatchProducer() throws Exception {
        // 同 note 两赞 + 另一 note 一取消的数组 payload
        String body = JsonUtils.toJsonString(List.of(
                event(1L, 100L, 7L, 1),
                event(2L, 100L, 7L, 1),
                event(3L, 200L, 8L, 0)));

        invokeConsumeMessage(body);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rocketMQTemplate).syncSend(eq(MQConstants.TOPIC_COUNT_NOTE_LIKE_2_DB), captor.capture());
        String payload = (String) captor.getValue().getPayload();
        List<AggregationCountLikeUnlikeNoteMqDTO> aggregated = JsonUtils.parseList(payload, AggregationCountLikeUnlikeNoteMqDTO.class);
        Map<Long, AggregationCountLikeUnlikeNoteMqDTO> byNote = aggregated.stream()
                .collect(Collectors.toMap(AggregationCountLikeUnlikeNoteMqDTO::getNoteId, Function.identity()));

        assertEquals(2, byNote.get(100L).getCount(), "同 note 两赞应聚合为 +2");
        assertEquals(7L, byNote.get(100L).getCreatorId());
        assertEquals(-1, byNote.get(200L).getCount(), "一取消应聚合为 -1");
        assertEquals(8L, byNote.get(200L).getCreatorId());
    }

    @Test
    void shouldStillAcceptLegacySingleObjectMessage() throws Exception {
        String body = JsonUtils.toJsonString(event(1L, 300L, 9L, 1));

        invokeConsumeMessage(body);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rocketMQTemplate).syncSend(eq(MQConstants.TOPIC_COUNT_NOTE_LIKE_2_DB), captor.capture());
        String payload = (String) captor.getValue().getPayload();
        assertTrue(payload.contains("\"noteId\":300"));
    }

    private CountLikeUnlikeNoteMqDTO event(Long userId, Long noteId, Long creatorId, Integer type) {
        return CountLikeUnlikeNoteMqDTO.builder()
                .userId(userId)
                .noteId(noteId)
                .noteCreatorId(creatorId)
                .type(type)
                .createTime(LocalDateTime.of(2026, 8, 16, 12, 0))
                .build();
    }

    private void invokeConsumeMessage(String body) throws Exception {
        java.lang.reflect.Method m = CountNoteLikeConsumer.class.getDeclaredMethod("consumeMessage", List.class);
        m.setAccessible(true);
        m.invoke(consumer, List.of(body));
    }
}
