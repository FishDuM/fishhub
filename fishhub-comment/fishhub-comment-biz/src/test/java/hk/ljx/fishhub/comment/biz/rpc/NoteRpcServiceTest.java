package hk.ljx.fishhub.comment.biz.rpc;

import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.note.api.NoteFeignApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteRpcServiceTest {

    @Mock
    private NoteFeignApi noteFeignApi;

    @InjectMocks
    private NoteRpcService noteRpcService;

    @BeforeEach
    void setUp() {
        NoteRpcService.invalidateAll();
    }

    @Test
    void shouldHitLocalCacheOnSubsequentAccessibleCalls() {
        Long noteId = 99999L;
        when(noteFeignApi.isAccessible(noteId)).thenReturn(Response.success(true));

        boolean first = noteRpcService.isAccessible(noteId);
        boolean second = noteRpcService.isAccessible(noteId);
        boolean third = noteRpcService.isAccessible(noteId);

        assertTrue(first);
        assertTrue(second);
        assertTrue(third);

        // 连续调用 3 次，只有第 1 次穿透调用 Feign RPC，后续 2 次全部命中 Caffeine 本地缓存！
        verify(noteFeignApi, times(1)).isAccessible(noteId);
    }

    @Test
    void shouldRefetchAfterInvalidate() {
        Long noteId = 88888L;
        when(noteFeignApi.isAccessible(noteId)).thenReturn(Response.success(true));

        assertTrue(noteRpcService.isAccessible(noteId));
        verify(noteFeignApi, times(1)).isAccessible(noteId);

        NoteRpcService.invalidate(noteId);

        assertTrue(noteRpcService.isAccessible(noteId));
        verify(noteFeignApi, times(2)).isAccessible(noteId);
    }
}
