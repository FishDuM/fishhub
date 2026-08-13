package hk.ljx.fishhub.note.biz.service;

import hk.ljx.fishhub.note.biz.domain.dataobject.NoteDO;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteDOMapper;
import hk.ljx.fishhub.note.biz.retry.ReliableMqOutbox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class NotePersistenceServiceTest {

    @Mock
    private NoteDOMapper noteDOMapper;
    @Mock
    private ReliableMqOutbox reliableMqOutbox;
    @InjectMocks
    private NotePersistenceService service;

    @Test
    void shouldPersistNoteBeforeEnqueueingItsEvent() {
        NoteDO note = NoteDO.builder().id(1001L).build();

        service.savePublishedNote(note, "NoteOperateTopic:PUBLISH", "event-body");

        var ordered = inOrder(noteDOMapper, reliableMqOutbox);
        ordered.verify(noteDOMapper).insert(note);
        ordered.verify(reliableMqOutbox).enqueue("NoteOperateTopic:PUBLISH", "event-body");
        ordered.verify(reliableMqOutbox).enqueue(
                hk.ljx.fishhub.note.biz.constant.MQConstants.TOPIC_INVALIDATE_NOTE_REDIS_CACHE,
                "event-body");
    }
}
