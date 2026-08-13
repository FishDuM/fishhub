package hk.ljx.fishhub.note.biz.service;

import hk.ljx.fishhub.note.biz.constant.MQConstants;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteCollectionDO;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteLikeDO;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteCollectionDOMapper;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteLikeDOMapper;
import hk.ljx.fishhub.note.biz.retry.ReliableMqOutbox;
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
    private ReliableMqOutbox outbox;
    @InjectMocks
    private NoteInteractionPersistenceService service;

    @Test
    void shouldPersistLikeBeforeEnqueueingCountEvent() {
        NoteLikeDO like = NoteLikeDO.builder().userId(1L).noteId(2L).build();
        when(noteLikeDOMapper.insertOrUpdate(like)).thenReturn(1);

        service.saveLike(like, "event-body");

        var ordered = inOrder(noteLikeDOMapper, outbox);
        ordered.verify(noteLikeDOMapper).insertOrUpdate(like);
        ordered.verify(outbox).enqueue(MQConstants.TOPIC_COUNT_NOTE_LIKE, "event-body");
    }

    @Test
    void shouldNotEnqueueLikeCountWhenStateDidNotChange() {
        NoteLikeDO like = NoteLikeDO.builder().userId(1L).noteId(2L).build();
        when(noteLikeDOMapper.insertOrUpdate(like)).thenReturn(0);

        service.saveLike(like, "event-body");

        verify(outbox, never()).enqueue(MQConstants.TOPIC_COUNT_NOTE_LIKE, "event-body");
    }

    @Test
    void shouldNotEnqueueUnlikeCountWhenStateDidNotChange() {
        NoteLikeDO like = NoteLikeDO.builder().userId(1L).noteId(2L).build();
        when(noteLikeDOMapper.update2UnlikeByUserIdAndNoteId(like)).thenReturn(0);

        service.saveUnlike(like, "event-body");

        verify(outbox, never()).enqueue(MQConstants.TOPIC_COUNT_NOTE_LIKE, "event-body");
    }

    @Test
    void shouldEnqueueCollectCountOnlyAfterStateChanged() {
        NoteCollectionDO collection = NoteCollectionDO.builder().userId(1L).noteId(2L).build();
        when(noteCollectionDOMapper.insertOrUpdate(collection)).thenReturn(1);

        service.saveCollect(collection, "event-body");

        var ordered = inOrder(noteCollectionDOMapper, outbox);
        ordered.verify(noteCollectionDOMapper).insertOrUpdate(collection);
        ordered.verify(outbox).enqueue(MQConstants.TOPIC_COUNT_NOTE_COLLECT, "event-body");
    }

    @Test
    void shouldNotEnqueueUncollectCountWhenStateDidNotChange() {
        NoteCollectionDO collection = NoteCollectionDO.builder().userId(1L).noteId(2L).build();
        when(noteCollectionDOMapper.update2UnCollectByUserIdAndNoteId(collection)).thenReturn(0);

        service.saveUncollect(collection, "event-body");

        verify(outbox, never()).enqueue(MQConstants.TOPIC_COUNT_NOTE_COLLECT, "event-body");
    }
}
