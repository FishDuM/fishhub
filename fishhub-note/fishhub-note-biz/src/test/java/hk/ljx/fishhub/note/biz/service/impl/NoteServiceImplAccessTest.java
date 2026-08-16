package hk.ljx.fishhub.note.biz.service.impl;

import hk.ljx.fishhub.note.biz.domain.dataobject.NoteDO;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteDOMapper;
import hk.ljx.fishhub.note.api.NoteWriteAccessCheckReqDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteServiceImplAccessTest {

    @Mock
    private NoteDOMapper noteDOMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @InjectMocks
    private NoteServiceImpl service;

    @Test
    void shouldUseOneBatchQueryWhenAccessSnapshotsAreCold() {
        List<Long> noteIds = List.of(11L, 12L, 13L);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(List.of("note:access:11", "note:access:12", "note:access:13")))
                .thenReturn(Arrays.asList(null, null, null));
        when(noteDOMapper.selectAccessInfosByNoteIds(noteIds)).thenReturn(List.of(
                NoteDO.builder().id(11L).creatorId(1L).visible(0).build(),
                NoteDO.builder().id(12L).creatorId(2L).visible(0).build()));

        var response = service.findAccessibleNoteIds(noteIds);

        assertEquals(List.of(11L, 12L), response.getData());
        verify(noteDOMapper).selectAccessInfosByNoteIds(noteIds);
        verify(noteDOMapper, never()).selectAccessInfoByNoteId(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void shouldFallBackToOneBatchQueryWhenRedisIsUnavailable() {
        List<Long> noteIds = List.of(11L, 12L);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(List.of("note:access:11", "note:access:12")))
                .thenThrow(new IllegalStateException("redis unavailable"));
        when(noteDOMapper.selectAccessInfosByNoteIds(noteIds)).thenReturn(List.of(
                NoteDO.builder().id(11L).creatorId(1L).visible(0).build(),
                NoteDO.builder().id(12L).creatorId(2L).visible(0).build()));

        var response = service.findAccessibleNoteIds(noteIds);

        assertEquals(noteIds, response.getData());
        verify(noteDOMapper).selectAccessInfosByNoteIds(noteIds);
    }

    @Test
    void shouldReloadAccessSnapshotWhenCachedJsonIsCorrupted() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("note:access:11")).thenReturn("{");
        when(noteDOMapper.selectAccessInfoByNoteId(11L)).thenReturn(
                NoteDO.builder().id(11L).creatorId(1L).visible(0).build());

        var response = service.isAccessible(11L);

        assertEquals(Boolean.TRUE, response.getData());
        verify(noteDOMapper).selectAccessInfoByNoteId(11L);
    }

    @Test
    void shouldCheckWritableNotesDirectlyFromMySql() {
        List<NoteWriteAccessCheckReqDTO> requests = List.of(
                NoteWriteAccessCheckReqDTO.builder().noteId(11L).userId(101L).build(),
                NoteWriteAccessCheckReqDTO.builder().noteId(12L).userId(102L).build(),
                NoteWriteAccessCheckReqDTO.builder().noteId(13L).userId(103L).build());
        when(noteDOMapper.selectAccessInfosByNoteIds(List.of(11L, 12L, 13L))).thenReturn(List.of(
                NoteDO.builder().id(11L).creatorId(1L).visible(0).build(),
                NoteDO.builder().id(12L).creatorId(102L).visible(1).build(),
                NoteDO.builder().id(13L).creatorId(1L).visible(1).build()));

        var response = service.findWritableNoteAccesses(requests);

        assertEquals(List.of(requests.get(0), requests.get(1)), response.getData());
        verify(noteDOMapper).selectAccessInfosByNoteIds(List.of(11L, 12L, 13L));
        verify(stringRedisTemplate, never()).opsForValue();
    }
}
