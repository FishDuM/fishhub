package hk.ljx.fishhub.note.biz.retry;

import hk.ljx.fishhub.note.biz.domain.dataobject.MqSendFailureDO;
import hk.ljx.fishhub.note.biz.domain.mapper.MqSendFailureMapper;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReliableMqOutboxTest {

    @Mock
    private MqSendFailureMapper mapper;
    @Mock
    private RocketMQTemplate rocketMQTemplate;
    @InjectMocks
    private ReliableMqOutbox outbox;

    @Test
    void shouldPersistPendingEventBeforeItCanBeSent() {
        outbox.enqueue("NoteOperateTopic:publishNote", "event-body");

        ArgumentCaptor<MqSendFailureDO> captor = ArgumentCaptor.forClass(MqSendFailureDO.class);
        verify(mapper).insertPending(captor.capture());
        assertEquals("NoteOperateTopic:publishNote", captor.getValue().getTopic());
        assertEquals("event-body", captor.getValue().getBody());
        assertNotNull(captor.getValue().getMessageKey());
    }

    @Test
    void shouldKeepPendingEventWhenImmediateSendFails() {
        doThrow(new RuntimeException("broker unavailable"))
                .when(rocketMQTemplate).syncSend(eq("NoteOperateTopic:publishNote"), any(Message.class));

        outbox.sendNow("NoteOperateTopic:publishNote", "event-body");

        verify(mapper, never()).deleteByMessageKey(any());
    }
}
