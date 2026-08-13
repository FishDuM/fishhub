package hk.ljx.fishhub.note.biz.listener;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.note.biz.constant.MQConstants;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteDOMapper;
import hk.ljx.fishhub.note.biz.model.dto.PublishNoteDTO;
import hk.ljx.fishhub.note.biz.retry.ReliableMqOutbox;
import hk.ljx.fishhub.note.biz.rpc.KeyValueRpcService;
import hk.ljx.fishhub.note.biz.service.NotePersistenceService;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublishNote2DBLocalTransactionListenerTest {

    @Mock
    private NoteDOMapper noteDOMapper;
    @Mock
    private ReliableMqOutbox reliableMqOutbox;
    @Mock
    private NotePersistenceService notePersistenceService;
    @Mock
    private KeyValueRpcService keyValueRpcService;
    @InjectMocks
    private PublishNote2DBLocalTransactionListener listener;

    @Test
    void shouldSaveContentBeforePersistingNoteMetadata() {
        when(keyValueRpcService.saveNoteContent("content-1", "正文"))
                .thenReturn(true);

        RocketMQLocalTransactionState state = listener.executeLocalTransaction(message(), null);

        assertEquals(RocketMQLocalTransactionState.COMMIT, state);
        verify(keyValueRpcService).saveNoteContent("content-1", "正文");
        verify(notePersistenceService).savePublishedNote(any(), any(), any());
        verify(reliableMqOutbox).sendNow(eq(MQConstants.TOPIC_INVALIDATE_NOTE_REDIS_CACHE), any());
        verify(reliableMqOutbox).sendNow(
                eq(MQConstants.TOPIC_NOTE_OPERATE + ":" + MQConstants.TAG_NOTE_PUBLISH), any());
    }

    @Test
    void shouldRollBackWithoutMetadataWhenContentCannotBeSaved() {
        when(keyValueRpcService.saveNoteContent("content-1", "正文"))
                .thenReturn(false);
        when(keyValueRpcService.deleteNoteContent("content-1"))
                .thenReturn(true);

        RocketMQLocalTransactionState state = listener.executeLocalTransaction(message(), null);

        assertEquals(RocketMQLocalTransactionState.ROLLBACK, state);
        verify(notePersistenceService, never()).savePublishedNote(any(), any(), any());
        verify(keyValueRpcService).deleteNoteContent("content-1");
    }

    @Test
    void shouldCompensateContentWhenMetadataInsertFails() {
        when(keyValueRpcService.saveNoteContent("content-1", "正文"))
                .thenReturn(true);
        when(keyValueRpcService.deleteNoteContent("content-1"))
                .thenReturn(true);
        doThrow(new RuntimeException("database unavailable"))
                .when(notePersistenceService).savePublishedNote(any(), any(), any());

        RocketMQLocalTransactionState state = listener.executeLocalTransaction(message(), null);

        assertEquals(RocketMQLocalTransactionState.ROLLBACK, state);
        verify(keyValueRpcService).deleteNoteContent("content-1");
    }

    private Message<byte[]> message() {
        PublishNoteDTO dto = PublishNoteDTO.builder()
                .id(1L)
                .creatorId(2L)
                .contentUuid("content-1")
                .content("正文")
                .build();
        return MessageBuilder.withPayload(JsonUtils.toJsonString(dto).getBytes(StandardCharsets.UTF_8)).build();
    }
}
