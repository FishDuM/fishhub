package hk.ljx.fishhub.count.client;

import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.count.api.CountFeignApi;
import hk.ljx.fishhub.count.dto.FindNoteCountsByIdRspDTO;
import hk.ljx.fishhub.count.dto.FindUserCountsByIdRspDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CountClientTest {

    @Mock
    private CountFeignApi countFeignApi;

    @InjectMocks
    private CountClient countClient;

    @BeforeEach
    void setUp() {
        CountClient.invalidateAllNotes();
        CountClient.invalidateAllUsers();
    }

    @Test
    void shouldHitLocalCacheOnSubsequentNoteCountQueries() {
        Long noteId = 1001L;
        FindNoteCountsByIdRspDTO dto = FindNoteCountsByIdRspDTO.builder()
                .noteId(noteId)
                .likeTotal(10L)
                .collectTotal(5L)
                .commentTotal(2L)
                .build();
        when(countFeignApi.findNotesCount(any())).thenReturn(Response.success(List.of(dto)));

        List<FindNoteCountsByIdRspDTO> first = countClient.findByNoteIds(List.of(noteId));
        List<FindNoteCountsByIdRspDTO> second = countClient.findByNoteIds(List.of(noteId));
        List<FindNoteCountsByIdRspDTO> third = countClient.findByNoteIds(List.of(noteId));

        assertEquals(1, first.size());
        assertEquals(10L, first.get(0).getLikeTotal());
        assertEquals(1, second.size());
        assertEquals(1, third.size());

        // 连续查 3 次，只有第 1 次调 Feign RPC，后 2 次全走本地 Caffeine 缓存
        verify(countFeignApi, times(1)).findNotesCount(any());
    }

    @Test
    void shouldRefetchNoteCountAfterInvalidate() {
        Long noteId = 1002L;
        FindNoteCountsByIdRspDTO dto = FindNoteCountsByIdRspDTO.builder()
                .noteId(noteId)
                .likeTotal(100L)
                .build();
        when(countFeignApi.findNotesCount(any())).thenReturn(Response.success(List.of(dto)));

        assertEquals(1, countClient.findByNoteIds(List.of(noteId)).size());
        verify(countFeignApi, times(1)).findNotesCount(any());

        // 用户点赞后，主动失效该笔记本地缓存
        CountClient.invalidate(noteId);

        // 再次查询立即穿透重新拉取，保证数据刷新强一致
        assertEquals(1, countClient.findByNoteIds(List.of(noteId)).size());
        verify(countFeignApi, times(2)).findNotesCount(any());
    }

    @Test
    void shouldHitLocalCacheOnUserCountQuery() {
        Long userId = 2001L;
        FindUserCountsByIdRspDTO dto = FindUserCountsByIdRspDTO.builder()
                .userId(userId)
                .fansTotal(50L)
                .followingTotal(20L)
                .build();
        when(countFeignApi.findUserCount(any())).thenReturn(Response.success(dto));

        FindUserCountsByIdRspDTO first = countClient.findUserCountById(userId);
        FindUserCountsByIdRspDTO second = countClient.findUserCountById(userId);

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(50L, first.getFansTotal());

        verify(countFeignApi, times(1)).findUserCount(any());
    }
}
