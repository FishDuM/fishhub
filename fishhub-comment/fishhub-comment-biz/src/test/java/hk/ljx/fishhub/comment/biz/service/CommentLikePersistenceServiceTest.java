package hk.ljx.fishhub.comment.biz.service;

import hk.ljx.framework.mq.tx.TxJournalStore;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentLikeDOMapper;
import hk.ljx.fishhub.comment.biz.enums.LikeUnlikeCommentTypeEnum;
import hk.ljx.fishhub.comment.biz.model.dto.LikeUnlikeCommentMqDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentLikePersistenceServiceTest {

    @Mock
    private CommentLikeDOMapper commentLikeDOMapper;
    @Mock
    private TxJournalStore txJournalStore;
    @InjectMocks
    private CommentLikePersistenceService service;

    @Test
    void shouldRecordJournalOnlyWhenLikeWasInserted() {
        LikeUnlikeCommentMqDTO operation = operation(LikeUnlikeCommentTypeEnum.LIKE.getCode());
        when(commentLikeDOMapper.insertIfAbsent(operation)).thenReturn(1);

        assertTrue(service.apply(operation, "tx-1"));

        verify(txJournalStore).record("tx-1");
    }

    @Test
    void shouldIgnoreDuplicateLike() {
        LikeUnlikeCommentMqDTO operation = operation(LikeUnlikeCommentTypeEnum.LIKE.getCode());
        when(commentLikeDOMapper.insertIfAbsent(operation)).thenReturn(0);

        assertFalse(service.apply(operation, "tx-1"));

        verify(txJournalStore, never()).record("tx-1");
    }

    @Test
    void shouldRecordJournalOnlyWhenUnlikeDeletedARow() {
        LikeUnlikeCommentMqDTO operation = operation(LikeUnlikeCommentTypeEnum.UNLIKE.getCode());
        when(commentLikeDOMapper.deleteIfPresent(operation)).thenReturn(1);

        assertTrue(service.apply(operation, "tx-1"));

        verify(txJournalStore).record("tx-1");
    }

    private LikeUnlikeCommentMqDTO operation(Integer type) {
        return LikeUnlikeCommentMqDTO.builder().userId(1L).commentId(2L).type(type).build();
    }
}
