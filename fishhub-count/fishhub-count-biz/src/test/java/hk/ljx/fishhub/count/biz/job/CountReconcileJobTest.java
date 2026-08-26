package hk.ljx.fishhub.count.biz.job;

import hk.ljx.fishhub.count.biz.domain.dataobject.IdCountBO;
import hk.ljx.fishhub.count.biz.domain.mapper.CountReconcileDOMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CountReconcileJobTest {

    @Mock
    private CountReconcileDOMapper countReconcileDOMapper;

    @InjectMocks
    private CountReconcileJob countReconcileJob;

    @Test
    @DisplayName("测试计数对账全流程成功推进与批量更新")
    void testReconcileSuccess() {
        // Mock 笔记批次
        when(countReconcileDOMapper.selectNextNoteIds(eq(0L), anyInt()))
                .thenReturn(List.of(1001L, 1002L));
        when(countReconcileDOMapper.selectNextNoteIds(eq(1002L), anyInt()))
                .thenReturn(Collections.emptyList());
        when(countReconcileDOMapper.countNoteLikes(any()))
                .thenReturn(List.of(new IdCountBO(1001L, 10L)));
        when(countReconcileDOMapper.countNoteCollections(any()))
                .thenReturn(List.of(new IdCountBO(1001L, 5L)));
        when(countReconcileDOMapper.countNoteComments(any()))
                .thenReturn(List.of(new IdCountBO(1001L, 3L)));

        // Mock 用户批次
        when(countReconcileDOMapper.selectNextUserIds(eq(0L), anyInt()))
                .thenReturn(List.of(2001L));
        when(countReconcileDOMapper.selectNextUserIds(eq(2001L), anyInt()))
                .thenReturn(Collections.emptyList());
        when(countReconcileDOMapper.countUserFans(any()))
                .thenReturn(List.of(new IdCountBO(2001L, 20L)));
        when(countReconcileDOMapper.countUserFollowings(any()))
                .thenReturn(List.of(new IdCountBO(2001L, 15L)));
        when(countReconcileDOMapper.countUserNotes(any()))
                .thenReturn(List.of(new IdCountBO(2001L, 2L)));
        when(countReconcileDOMapper.countUserLikes(any()))
                .thenReturn(List.of(new IdCountBO(2001L, 100L)));
        when(countReconcileDOMapper.countUserCollections(any()))
                .thenReturn(List.of(new IdCountBO(2001L, 50L)));

        // Mock 评论批次
        when(countReconcileDOMapper.selectNextCommentIds(eq(0L), anyInt()))
                .thenReturn(List.of(3001L));
        when(countReconcileDOMapper.selectNextCommentIds(eq(3001L), anyInt()))
                .thenReturn(Collections.emptyList());
        when(countReconcileDOMapper.countCommentLikes(any()))
                .thenReturn(List.of(new IdCountBO(3001L, 8L)));
        when(countReconcileDOMapper.countChildComments(any()))
                .thenReturn(List.of(new IdCountBO(3001L, 4L)));

        // 执行对账任务
        countReconcileJob.reconcile();

        // 验证各批量操作均被触发且游标正确终止
        verify(countReconcileDOMapper, times(1)).batchUpsertNoteCounts(any());
        verify(countReconcileDOMapper, times(1)).batchUpsertUserCounts(any());
        verify(countReconcileDOMapper, times(1)).batchUpdateCommentCounts(any());
    }
}
