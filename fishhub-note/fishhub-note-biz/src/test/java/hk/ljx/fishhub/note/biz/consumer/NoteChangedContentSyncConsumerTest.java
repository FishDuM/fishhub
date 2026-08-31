package hk.ljx.fishhub.note.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.note.api.NoteChangedEventMqDTO;
import hk.ljx.fishhub.note.api.NoteContentTaskMqDTO;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteContentDO;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteDO;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteDOMapper;
import hk.ljx.fishhub.note.biz.domain.repository.NoteContentRepository;
import hk.ljx.fishhub.note.biz.enums.NoteContentTaskTypeEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NoteChangedContentSyncConsumerTest {

    @Mock
    private NoteContentRepository noteContentRepository;
    @Mock
    private NoteDOMapper noteDOMapper;
    @InjectMocks
    private NoteChangedContentSyncConsumer consumer;

    private static final String UUID_NEW = "a0000000-0000-0000-0000-000000000001";
    private static final String UUID_DEL = "a0000000-0000-0000-0000-000000000002";
    private static final String UUID_CUR = "a0000000-0000-0000-0000-000000000003";
    private static final String UUID_STALE = "a0000000-0000-0000-0000-000000000004";
    private static final String UUID_AFTER = "a0000000-0000-0000-0000-000000000005";

    @Test
    void shouldShareSinglePreAndPostCheckAcrossTasks() {
        when(noteDOMapper.selectByPrimaryKey(5L)).thenReturn(
                NoteDO.builder().id(5L).contentUuid(UUID_NEW).build());

        consumer.onMessage(body(List.of(
                task(UUID_NEW, "c1", NoteContentTaskTypeEnum.UPSERT.name()),
                task(UUID_NEW, "c2", NoteContentTaskTypeEnum.UPSERT.name()))));

        verify(noteDOMapper, times(2)).selectByPrimaryKey(5L);
        verify(noteContentRepository, times(2)).save(any(NoteContentDO.class));
        verify(noteContentRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    void shouldDeleteDirectlyForDeleteTaskWithoutCheckingNote() {
        consumer.onMessage(body(List.of(
                task(UUID_DEL, null, NoteContentTaskTypeEnum.DELETE.name()))));

        verify(noteContentRepository).deleteById(UUID.fromString(UUID_DEL));
        verify(noteDOMapper, never()).selectByPrimaryKey(anyLong());
        verify(noteContentRepository, never()).save(any(NoteContentDO.class));
    }

    @Test
    void shouldCleanStaleTaskWhenContentUuidMismatch() {
        when(noteDOMapper.selectByPrimaryKey(5L)).thenReturn(
                NoteDO.builder().id(5L).contentUuid(UUID_CUR).build());

        consumer.onMessage(body(List.of(
                task(UUID_STALE, "c1", NoteContentTaskTypeEnum.UPSERT.name()))));

        verify(noteContentRepository).deleteById(UUID.fromString(UUID_STALE));
        verify(noteContentRepository, never()).save(any(NoteContentDO.class));
    }

    @Test
    void shouldCleanJustWrittenContentWhenDeletedDuringWrite() {
        when(noteDOMapper.selectByPrimaryKey(5L)).thenReturn(
                NoteDO.builder().id(5L).contentUuid(UUID_NEW).build(),
                NoteDO.builder().id(5L).contentUuid(UUID_AFTER).build());

        consumer.onMessage(body(List.of(
                task(UUID_NEW, "c1", NoteContentTaskTypeEnum.UPSERT.name()))));

        verify(noteContentRepository).save(any(NoteContentDO.class));
        verify(noteContentRepository).deleteById(UUID.fromString(UUID_NEW));
    }

    @Test
    void shouldAcknowledgeCleanlyWhenContentTasksAreEmpty() {
        consumer.onMessage(body(List.of()));

        verify(noteDOMapper, never()).selectByPrimaryKey(anyLong());
        verify(noteContentRepository, never()).save(any(NoteContentDO.class));
        verify(noteContentRepository, never()).deleteById(any(UUID.class));
    }

    private String body(List<NoteContentTaskMqDTO> tasks) {
        return JsonUtils.toJsonString(NoteChangedEventMqDTO.builder()
                .noteId(5L)
                .contentTasks(tasks)
                .build());
    }

    private NoteContentTaskMqDTO task(String uuid, String content, String type) {
        return NoteContentTaskMqDTO.builder()
                .noteId(5L)
                .contentUuid(uuid)
                .content(content)
                .type(type)
                .build();
    }
}
