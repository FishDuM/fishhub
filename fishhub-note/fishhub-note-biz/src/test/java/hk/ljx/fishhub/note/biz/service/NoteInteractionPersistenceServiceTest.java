package hk.ljx.fishhub.note.biz.service;

import hk.ljx.framework.mq.tx.TxJournalStore;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteCollectionDO;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteLikeDO;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteCollectionDOMapper;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteLikeDOMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteInteractionPersistenceServiceTest {

    @Mock
    private NoteLikeDOMapper noteLikeDOMapper;
    @Mock
    private NoteCollectionDOMapper noteCollectionDOMapper;
    @Mock
    private TxJournalStore txJournalStore;
    @InjectMocks
    private NoteInteractionPersistenceService service;

    @Test
    void shouldPersistLikeThenRecordJournal() {
        NoteLikeDO like = NoteLikeDO.builder().userId(1L).noteId(2L).build();
        when(noteLikeDOMapper.insertOrUpdate(like)).thenReturn(1);

        service.saveLike(like, "tx-1");

        var ordered = inOrder(noteLikeDOMapper, txJournalStore);
        ordered.verify(noteLikeDOMapper).insertOrUpdate(like);
        ordered.verify(txJournalStore).record("tx-1");
    }

    @Test
    void shouldNotRecordJournalWhenLikeStateDidNotChange() {
        NoteLikeDO like = NoteLikeDO.builder().userId(1L).noteId(2L).build();
        when(noteLikeDOMapper.insertOrUpdate(like)).thenReturn(0);

        service.saveLike(like, "tx-1");

        verify(txJournalStore, never()).record("tx-1");
    }

    @Test
    void shouldNotRecordJournalWhenUnlikeStateDidNotChange() {
        NoteLikeDO like = NoteLikeDO.builder().userId(1L).noteId(2L).build();
        when(noteLikeDOMapper.update2UnlikeByUserIdAndNoteId(like)).thenReturn(0);

        service.saveUnlike(like, "tx-1");

        verify(txJournalStore, never()).record("tx-1");
    }

    @Test
    void shouldPersistCollectThenRecordJournal() {
        NoteCollectionDO collection = NoteCollectionDO.builder().userId(1L).noteId(2L).build();
        when(noteCollectionDOMapper.insertOrUpdate(collection)).thenReturn(1);

        service.saveCollect(collection, "tx-1");

        var ordered = inOrder(noteCollectionDOMapper, txJournalStore);
        ordered.verify(noteCollectionDOMapper).insertOrUpdate(collection);
        ordered.verify(txJournalStore).record("tx-1");
    }

    @Test
    void shouldNotRecordJournalWhenUncollectStateDidNotChange() {
        NoteCollectionDO collection = NoteCollectionDO.builder().userId(1L).noteId(2L).build();
        when(noteCollectionDOMapper.update2UnCollectByUserIdAndNoteId(collection)).thenReturn(0);

        service.saveUncollect(collection, "tx-1");

        verify(txJournalStore, never()).record("tx-1");
    }
}
