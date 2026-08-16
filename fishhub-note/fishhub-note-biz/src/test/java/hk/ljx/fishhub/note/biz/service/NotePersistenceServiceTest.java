package hk.ljx.fishhub.note.biz.service;

import hk.ljx.framework.mq.tx.TxJournalStore;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteDO;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteDOMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotePersistenceServiceTest {

    @Mock
    private NoteDOMapper noteDOMapper;
    @Mock
    private TxJournalStore txJournalStore;
    @InjectMocks
    private NotePersistenceService service;

    @Test
    void shouldPersistNoteThenRecordJournal() {
        NoteDO note = NoteDO.builder().id(1001L).build();

        service.savePublishedNote(note, "tx-1");

        var ordered = inOrder(noteDOMapper, txJournalStore);
        ordered.verify(noteDOMapper).insert(note);
        ordered.verify(txJournalStore).record("tx-1");
    }

    @Test
    void shouldNotRecordJournalWhenUpdateLosesRevisionRace() {
        when(noteDOMapper.updateByPrimaryKeyAndRevision(any())).thenReturn(0);

        assertThatThrownBy(() -> service.updateNote(NoteDO.builder().id(1001L).build(), "tx-1"))
                .isInstanceOf(hk.ljx.framework.common.exception.BizException.class);

        verify(txJournalStore, never()).record("tx-1");
    }

    @Test
    void shouldNotRecordJournalWhenDeleteLosesRevisionRace() {
        when(noteDOMapper.logicalDeleteByPrimaryKeyAndRevision(any())).thenReturn(0);

        assertThatThrownBy(() -> service.logicalDeleteNote(NoteDO.builder().id(1001L).build(), "tx-1"))
                .isInstanceOf(hk.ljx.framework.common.exception.BizException.class);

        verify(txJournalStore, never()).record("tx-1");
    }
}
