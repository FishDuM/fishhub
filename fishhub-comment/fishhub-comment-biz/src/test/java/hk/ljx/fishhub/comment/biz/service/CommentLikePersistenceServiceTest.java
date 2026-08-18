package hk.ljx.fishhub.comment.biz.service;

import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentLikeDOMapper;
import hk.ljx.fishhub.comment.biz.enums.LikeUnlikeCommentTypeEnum;
import hk.ljx.fishhub.comment.biz.model.dto.LikeUnlikeCommentMqDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentLikePersistenceServiceTest {

    @Mock
    private CommentLikeDOMapper commentLikeDOMapper;
    @Mock
    private CommentDOMapper commentDOMapper;
    @InjectMocks
    private CommentLikePersistenceService service;

    @Test
    void shouldBatchInsertRelationsAndIncreaseLikeTotalWhenLikesApplied() {
        LikeUnlikeCommentMqDTO like = operation(1L, 2L, LikeUnlikeCommentTypeEnum.LIKE.getCode());
        when(commentLikeDOMapper.batchInsert(anyList())).thenReturn(2);

        List<Long> applied = service.applyBatch(List.of(like, like));

        assertTrue(applied.contains(2L));
        // like_total 只按「真实新增行数」累加，同批重复/重放不重复加
        verify(commentDOMapper).updateLikeTotalByCommentId(2, 2L);
    }

    @Test
    void shouldIgnoreDuplicateLikesWithoutTouchingLikeTotal() {
        LikeUnlikeCommentMqDTO like = operation(1L, 2L, LikeUnlikeCommentTypeEnum.LIKE.getCode());
        when(commentLikeDOMapper.batchInsert(anyList())).thenReturn(0);

        List<Long> applied = service.applyBatch(List.of(like));

        assertTrue(applied.isEmpty());
        verify(commentDOMapper, never()).updateLikeTotalByCommentId(
                ArgumentMatchers.anyInt(), ArgumentMatchers.eq(2L));
    }

    @Test
    void shouldDeleteRelationsAndDecreaseLikeTotalWhenUnlikesApplied() {
        LikeUnlikeCommentMqDTO unlike = operation(1L, 3L, LikeUnlikeCommentTypeEnum.UNLIKE.getCode());
        when(commentLikeDOMapper.batchDelete(anyList())).thenReturn(1);

        List<Long> applied = service.applyBatch(List.of(unlike));

        assertTrue(applied.contains(3L));
        verify(commentDOMapper).updateLikeTotalByCommentId(-1, 3L);
    }

    @Test
    void shouldNetMixedBatchToZeroWithoutTouchingLikeTotalWhenInsertEqualsDelete() {
        LikeUnlikeCommentMqDTO like = operation(1L, 2L, LikeUnlikeCommentTypeEnum.LIKE.getCode());
        LikeUnlikeCommentMqDTO unlike = operation(1L, 2L, LikeUnlikeCommentTypeEnum.UNLIKE.getCode());
        // 与「同一用户最后操作生效」的合并一致：点赞+取消同批 → 关系净零
        when(commentLikeDOMapper.batchInsert(anyList())).thenReturn(1);
        when(commentLikeDOMapper.batchDelete(anyList())).thenReturn(1);

        List<Long> applied = service.applyBatch(List.of(like, unlike));

        assertTrue(applied.contains(2L));
        verify(commentDOMapper, never()).updateLikeTotalByCommentId(0, 2L);
    }

    @Test
    void shouldReturnEmptyWhenBatchIsEmpty() {
        List<Long> applied = service.applyBatch(List.of());
        assertEquals(List.of(), applied);
        verify(commentDOMapper, never()).updateLikeTotalByCommentId(ArgumentMatchers.anyInt(),
                ArgumentMatchers.anyLong());
    }

    private LikeUnlikeCommentMqDTO operation(Long userId, Long commentId, Integer type) {
        return LikeUnlikeCommentMqDTO.builder().userId(userId).commentId(commentId).type(type).build();
    }
}
