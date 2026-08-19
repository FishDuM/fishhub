package hk.ljx.fishhub.note.biz.service;

import hk.ljx.fishhub.count.dto.FindNoteCountsByIdRspDTO;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteDO;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteDOMapper;
import hk.ljx.fishhub.note.biz.model.vo.FindNoteActionListReqVO;
import hk.ljx.fishhub.count.client.CountClient;
import hk.ljx.fishhub.user.client.UserClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserNoteListServiceTest {

    @Mock
    private NoteDOMapper noteDOMapper;
    @Mock
    private UserClient userClient;
    @Mock
    private CountClient countClient;
    @Mock
    private NoteInteractionCacheService noteInteractionCacheService;
    @InjectMocks
    private UserNoteListService service;

    @Test
    void shouldUseLastActionAsNextCursor() {
        LocalDateTime cursorTime = LocalDateTime.of(2026, 8, 14, 16, 0, 0);
        FindNoteActionListReqVO request = FindNoteActionListReqVO.builder()
                .userId(1L)
                .cursorTime(cursorTime)
                .cursorId(31L)
                .build();
        LocalDateTime actionTime = LocalDateTime.of(2026, 8, 14, 15, 30, 0);
        List<NoteDO> noteDOS = List.of(
                NoteDO.builder().id(101L).creatorId(2L).actionId(32L).actionTime(actionTime.plusMinutes(1)).build(),
                NoteDO.builder().id(100L).creatorId(2L).actionId(31L).actionTime(actionTime).build());
        when(noteDOMapper.selectCollectedNoteListByUserIdAndCursor(1L, cursorTime, 31L)).thenReturn(noteDOS);
        when(userClient.findByIds(List.of(2L))).thenReturn(Collections.emptyList());
        when(countClient.findByNoteIds(List.of(101L, 100L))).thenReturn(Collections.<FindNoteCountsByIdRspDTO>emptyList());

        var response = service.findCollectedNotes(request);

        assertEquals(actionTime, response.getData().getNextCursorTime());
        assertEquals(31L, response.getData().getNextCursorId());
        verify(noteDOMapper).selectCollectedNoteListByUserIdAndCursor(1L, cursorTime, 31L);
    }
}
