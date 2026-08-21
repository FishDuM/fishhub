package hk.ljx.fishhub.note.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.framework.mq.tx.TransactionalMqSender;
import hk.ljx.framework.mq.tx.TxLocalTransaction;
import hk.ljx.fishhub.note.biz.constant.MQConstants;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteCollectionDO;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteDO;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteDOMapper;
import hk.ljx.fishhub.note.biz.enums.CollectUnCollectNoteTypeEnum;
import hk.ljx.fishhub.note.biz.model.dto.CollectUnCollectNoteMqDTO;
import hk.ljx.fishhub.note.biz.service.NoteInteractionCacheService;
import hk.ljx.fishhub.note.biz.service.NoteInteractionPersistenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectUnCollectNoteConsumerTest {

    @Mock
    private NoteDOMapper noteDOMapper;
    @Mock
    private NoteInteractionCacheService noteInteractionCacheService;
    @Mock
    private NoteInteractionPersistenceService persistenceService;
    @Mock
    private TransactionalMqSender transactionalMqSender;
    @InjectMocks
    private CollectUnCollectNoteConsumer consumer;

    private final LocalDateTime now = LocalDateTime.of(2026, 8, 16, 12, 0);

    @Test
    void shouldBatchCollectAndCarryNoteCreatorIdInPayload() {
        when(noteDOMapper.selectInteractionInfosByNoteIds(List.of(10L, 20L))).thenReturn(List.of(
                NoteDO.builder().id(10L).creatorId(99L).visible(0).status(1).build(),
                NoteDO.builder().id(20L).creatorId(88L).visible(0).status(1).build()));
        runLocalTx();

        consumer.consumeEventBodies(List.of(
                body(1L, 10L, CollectUnCollectNoteTypeEnum.COLLECT.getCode()),
                body(2L, 20L, CollectUnCollectNoteTypeEnum.COLLECT.getCode())));

        ArgumentCaptor<List<NoteCollectionDO>> doCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(persistenceService).saveNoteCollectBatch(doCaptor.capture(), anyString(), anyString(), anyString());
        assertEquals(2, doCaptor.getValue().size());
        verify(noteDOMapper).selectInteractionInfosByNoteIds(List.of(10L, 20L));
        verify(transactionalMqSender).sendInTransaction(
                eq(MQConstants.TOPIC_COUNT_NOTE_COLLECT), payloadCaptor.capture(), any());
        assertTrue(payloadCaptor.getValue().startsWith("["));
        assertTrue(payloadCaptor.getValue().contains("\"noteCreatorId\":99"));
        assertTrue(payloadCaptor.getValue().contains("\"noteCreatorId\":88"));
    }

    @Test
    void shouldDropCollectOnPrivateNoteAndRemoveSingleCacheRecordOnly() {
        when(noteDOMapper.selectInteractionInfosByNoteIds(List.of(10L))).thenReturn(List.of(
                NoteDO.builder().id(10L).creatorId(99L).visible(1).status(1).build()));

        consumer.consumeEventBodies(List.of(
                body(1L, 10L, CollectUnCollectNoteTypeEnum.COLLECT.getCode())));

        verify(noteInteractionCacheService).removeCollect(1L, 10L);
        verify(noteInteractionCacheService, never()).evictCollectCaches(1L);
        verify(persistenceService, never()).saveNoteCollectBatch(anyList(), anyString(), anyString(), anyString());
    }

    /**
     * 针对公开时期已收藏的笔记，即使作者后续转为私密，取消收藏仍应正常扣减计数并更新数据库状态
     */
    @Test
    void shouldAllowUncollectOnExistingNoteEvenIfTurnedPrivate() {
        when(noteDOMapper.selectInteractionInfosByNoteIds(List.of(10L))).thenReturn(List.of(
                NoteDO.builder().id(10L).creatorId(99L).visible(1).status(1).build()));
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        runLocalTx();

        consumer.consumeEventBodies(List.of(
                body(1L, 10L, CollectUnCollectNoteTypeEnum.UN_COLLECT.getCode())));

        verify(noteInteractionCacheService, never()).removeCollect(any(), any());
        verify(persistenceService).saveNoteCollectBatch(anyList(), anyString(), anyString(), anyString());
        verify(transactionalMqSender).sendInTransaction(
                eq(MQConstants.TOPIC_COUNT_NOTE_COLLECT), payloadCaptor.capture(), any());
        assertTrue(payloadCaptor.getValue().contains("\"noteId\":10"));
        assertTrue(payloadCaptor.getValue().contains("\"type\":0"));
    }

    private void runLocalTx() {
        org.mockito.Mockito.doAnswer(invocation -> {
            TxLocalTransaction localTx = invocation.getArgument(2);
            localTx.execute("tx-1");
            return null;
        }).when(transactionalMqSender).sendInTransaction(any(), any(), any());
    }

    private String body(Long userId, Long noteId, Integer type) {
        return JsonUtils.toJsonString(CollectUnCollectNoteMqDTO.builder()
                .userId(userId)
                .noteId(noteId)
                .type(type)
                .createTime(now)
                .build());
    }
}
