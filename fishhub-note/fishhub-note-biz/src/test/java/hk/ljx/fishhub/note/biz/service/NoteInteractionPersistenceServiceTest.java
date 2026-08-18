package hk.ljx.fishhub.note.biz.service;

import hk.ljx.framework.mq.tx.TxJournalStore;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteCollectionDO;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteLikeDO;
import hk.ljx.framework.mq.idempotent.MqConsumeRecordStore;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteCollectionDOMapper;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteLikeDOMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NoteInteractionPersistenceServiceTest {

    @Mock
    private NoteLikeDOMapper noteLikeDOMapper;
    @Mock
    private NoteCollectionDOMapper noteCollectionDOMapper;
    @Mock
    private MqConsumeRecordStore mqConsumeRecordStore;
    @Mock
    private TxJournalStore txJournalStore;
    @InjectMocks
    private NoteInteractionPersistenceService service;

    @Test
    void shouldPersistLikeBatchThenRecordJournal() {
        List<NoteLikeDO> likes = List.of(
                NoteLikeDO.builder().userId(1L).noteId(2L).build(),
                NoteLikeDO.builder().userId(1L).noteId(3L).build());

        boolean applied = service.saveNoteLikeBatch(likes, "group", "key-1", "tx-1");

        var ordered = inOrder(mqConsumeRecordStore, noteLikeDOMapper, txJournalStore);
        ordered.verify(mqConsumeRecordStore).insert("group", "key-1");
        ordered.verify(noteLikeDOMapper).insertOrUpdateBatch(likes);
        ordered.verify(txJournalStore).record("tx-1");
        org.junit.jupiter.api.Assertions.assertTrue(applied);
    }

    @Test
    void shouldSkipBatchWhenConsumeRecordAlreadyExists() {
        doThrow(new DuplicateKeyException("dup")).when(mqConsumeRecordStore).insert("group", "key-1");

        boolean applied = service.saveNoteLikeBatch(
                List.of(NoteLikeDO.builder().userId(1L).noteId(2L).build()), "group", "key-1", "tx-1");

        org.junit.jupiter.api.Assertions.assertFalse(applied);
        verify(noteLikeDOMapper, never()).insertOrUpdateBatch(anyList());
        verify(txJournalStore, never()).record("tx-1");
    }

    @Test
    void shouldPersistCollectBatchThenRecordJournal() {
        List<NoteCollectionDO> collections = List.of(
                NoteCollectionDO.builder().userId(1L).noteId(2L).build());

        boolean applied = service.saveNoteCollectBatch(collections, "group", "key-1", "tx-1");

        var ordered = inOrder(mqConsumeRecordStore, noteCollectionDOMapper, txJournalStore);
        ordered.verify(mqConsumeRecordStore).insert("group", "key-1");
        ordered.verify(noteCollectionDOMapper).insertOrUpdateBatch(collections);
        ordered.verify(txJournalStore).record("tx-1");
        org.junit.jupiter.api.Assertions.assertTrue(applied);
    }

    @Test
    void shouldSkipEmptyBatch() {
        org.junit.jupiter.api.Assertions.assertFalse(
                service.saveNoteLikeBatch(List.of(), "group", "key-1", "tx-1"));
        verify(mqConsumeRecordStore, never()).insert(anyString(), anyString());
    }
}
