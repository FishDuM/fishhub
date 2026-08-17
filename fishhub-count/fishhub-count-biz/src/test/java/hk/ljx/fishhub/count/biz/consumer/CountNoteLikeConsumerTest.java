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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CountNoteLikeConsumerTest {

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    @InjectMocks
    private CountNoteLikeConsumer consumer;

    @Test
    void shouldAggregateWholeBatchAndSendOnlyOneMessage() throws Exception {
        List<String> bodys = List.of(
                body(1L, 10L, 100L, 1),  // note10 点赞 +1
                body(2L, 10L, 100L, 0),  // note10 取消 -1 → 合并为 0
                body(3L, 20L, 200L, 1)   // note20 点赞 +1
        );

        ReflectionTestUtils.invokeMethod(consumer, "consumeMessage", bodys);

        ArgumentCaptor<Message<?>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        // 整批聚合后只发 1 条消息给 2DB
        verify(rocketMQTemplate, times(1)).syncSend(eq(MQConstants.TOPIC_COUNT_NOTE_LIKE_2_DB), messageCaptor.capture());
        List<AggregationCountLikeUnlikeNoteMqDTO> list = JsonUtils.parseList(
                String.valueOf(messageCaptor.getValue().getPayload()), AggregationCountLikeUnlikeNoteMqDTO.class);
        assertEquals(2, list.size());
        Map<Long, Integer> byNote = list.stream()
                .collect(Collectors.toMap(AggregationCountLikeUnlikeNoteMqDTO::getNoteId, AggregationCountLikeUnlikeNoteMqDTO::getCount));
        assertEquals(0, byNote.get(10L));
        assertEquals(1, byNote.get(20L));
        assertEquals(100L, list.stream().filter(i -> i.getNoteId().equals(10L)).findFirst().orElseThrow().getCreatorId());
    }

    private String body(long userId, long noteId, long noteCreatorId, int type) {
        return JsonUtils.toJsonString(CountLikeUnlikeNoteMqDTO.builder()
                .userId(userId)
                .noteId(noteId)
                .noteCreatorId(noteCreatorId)
                .type(type)
                .createTime(LocalDateTime.now())
                .build());
    }
}
