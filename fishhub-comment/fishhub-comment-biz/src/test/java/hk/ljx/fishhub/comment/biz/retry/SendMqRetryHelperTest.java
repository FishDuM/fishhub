package hk.ljx.fishhub.comment.biz.retry;

import hk.ljx.fishhub.comment.biz.domain.dataobject.MqSendFailureDO;
import hk.ljx.fishhub.comment.biz.domain.mapper.MqSendFailureMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SendMqRetryHelperTest {

    @Mock
    private RocketMQTemplate rocketMQTemplate;
    @Mock
    private MqSendFailureMapper mqSendFailureMapper;
    @InjectMocks
    private SendMqRetryHelper helper;

    @Test
    void shouldEnqueueCompleteOutboxRecord() {
        helper.enqueue("CountNoteCommentTopic", "event-body");

        ArgumentCaptor<MqSendFailureDO> captor = ArgumentCaptor.forClass(MqSendFailureDO.class);
        verify(mqSendFailureMapper).insertPending(captor.capture());
        assertEquals("CountNoteCommentTopic", captor.getValue().getTopic());
        assertEquals("event-body", captor.getValue().getBody());
        assertNotNull(captor.getValue().getMessageKey());
        assertNotNull(captor.getValue().getNextRetryTime());
    }

    @Test
    void shouldDeleteOutboxRecordOnlyAfterSuccessfulSend() {
        helper.sendNow("CountNoteCommentTopic", "event-body");

        verify(rocketMQTemplate).syncSend(eq("CountNoteCommentTopic"), any(Message.class));
        verify(mqSendFailureMapper).deleteByMessageKey(any());
    }
}
