package hk.ljx.fishhub.comment.biz.service;

import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentLikeDOMapper;
import hk.ljx.fishhub.comment.biz.enums.LikeUnlikeCommentTypeEnum;
import hk.ljx.fishhub.comment.biz.model.dto.LikeUnlikeCommentMqDTO;
import hk.ljx.fishhub.comment.biz.retry.SendMqRetryHelper;
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
    private SendMqRetryHelper sendMqRetryHelper;
    @InjectMocks
    private CommentLikePersistenceService service;

    @Test
    void shouldEnqueueCountEventOnlyWhenLikeWasInserted() {
        LikeUnlikeCommentMqDTO operation = operation(LikeUnlikeCommentTypeEnum.LIKE.getCode());
        when(commentLikeDOMapper.insertIfAbsent(operation)).thenReturn(1);

        assertTrue(service.apply(operation, "event"));

        verify(sendMqRetryHelper).enqueue(MQConstants.TOPIC_APPLIED_COMMENT_LIKE_OR_UNLIKE, "event");
    }

    @Test
    void shouldIgnoreDuplicateLike() {
        LikeUnlikeCommentMqDTO operation = operation(LikeUnlikeCommentTypeEnum.LIKE.getCode());
        when(commentLikeDOMapper.insertIfAbsent(operation)).thenReturn(0);

        assertFalse(service.apply(operation, "event"));

        verify(sendMqRetryHelper, never()).enqueue(MQConstants.TOPIC_APPLIED_COMMENT_LIKE_OR_UNLIKE, "event");
    }

    @Test
    void shouldEnqueueCountEventOnlyWhenUnlikeDeletedARow() {
        LikeUnlikeCommentMqDTO operation = operation(LikeUnlikeCommentTypeEnum.UNLIKE.getCode());
        when(commentLikeDOMapper.deleteIfPresent(operation)).thenReturn(1);

        assertTrue(service.apply(operation, "event"));

        verify(sendMqRetryHelper).enqueue(MQConstants.TOPIC_APPLIED_COMMENT_LIKE_OR_UNLIKE, "event");
    }

    private LikeUnlikeCommentMqDTO operation(Integer type) {
        return LikeUnlikeCommentMqDTO.builder().userId(1L).commentId(2L).type(type).build();
    }
}
