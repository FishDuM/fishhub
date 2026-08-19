package hk.ljx.fishhub.note.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.note.biz.constant.MQConstants;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteDO;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteDOMapper;
import hk.ljx.fishhub.note.biz.enums.NoteContentTaskTypeEnum;
import hk.ljx.fishhub.note.api.NoteChangedEventMqDTO;
import hk.ljx.fishhub.note.api.NoteContentTaskMqDTO;
import hk.ljx.fishhub.kv.client.KeyValueClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteChangedContentSyncConsumerTest {

    @Mock
    private KeyValueClient keyValueClient;
    @Mock
    private NoteDOMapper noteDOMapper;
    @InjectMocks
    private NoteChangedContentSyncConsumer consumer;

    @Test
    void shouldShareSinglePreAndPostCheckAcrossTasks() {
        when(noteDOMapper.selectByPrimaryKey(5L)).thenReturn(
                NoteDO.builder().id(5L).contentUuid("uuid-new").build());
        when(keyValueClient.saveNoteContent(eq("uuid-new"), anyString())).thenReturn(true);

        // 同一事件两个 UPSERT 任务：前查 + 后查各一次，而非每个任务两次
        consumer.onMessage(body(List.of(
                task("uuid-new", "c1", NoteContentTaskTypeEnum.UPSERT.name()),
                task("uuid-new", "c2", NoteContentTaskTypeEnum.UPSERT.name()))));

        verify(noteDOMapper, times(2)).selectByPrimaryKey(5L);
        verify(keyValueClient).saveNoteContent("uuid-new", "c1");
        verify(keyValueClient).saveNoteContent("uuid-new", "c2");
        verify(keyValueClient, never()).deleteNoteContent(anyString());
    }

    @Test
    void shouldDeleteDirectlyForDeleteTaskWithoutCheckingNote() {
        when(keyValueClient.deleteNoteContent("uuid-del")).thenReturn(true);
        consumer.onMessage(body(List.of(
                task("uuid-del", null, NoteContentTaskTypeEnum.DELETE.name()))));

        verify(keyValueClient).deleteNoteContent("uuid-del");
        verify(noteDOMapper, never()).selectByPrimaryKey(anyLong());
        verify(keyValueClient, never()).saveNoteContent(anyString(), anyString());
    }

    @Test
    void shouldCleanStaleTaskWhenContentUuidMismatch() {
        when(noteDOMapper.selectByPrimaryKey(5L)).thenReturn(
                NoteDO.builder().id(5L).contentUuid("uuid-current").build());

        consumer.onMessage(body(List.of(
                task("uuid-stale", "c1", NoteContentTaskTypeEnum.UPSERT.name()))));

        // 写前校验不匹配：清理旧正文，不写 KV
        verify(keyValueClient).deleteNoteContent("uuid-stale");
        verify(keyValueClient, never()).saveNoteContent(anyString(), anyString());
    }

    @Test
    void shouldCleanJustWrittenContentWhenDeletedDuringWrite() {
        when(noteDOMapper.selectByPrimaryKey(5L)).thenReturn(
                NoteDO.builder().id(5L).contentUuid("uuid-new").build(),
                NoteDO.builder().id(5L).contentUuid("uuid-new-after-delete").build());
        when(keyValueClient.saveNoteContent("uuid-new", "c1")).thenReturn(true);

        consumer.onMessage(body(List.of(
                task("uuid-new", "c1", NoteContentTaskTypeEnum.UPSERT.name()))));

        // 写后复核发现正文已更新：清理刚写入的旧正文
        verify(keyValueClient).saveNoteContent("uuid-new", "c1");
        verify(keyValueClient).deleteNoteContent("uuid-new");
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
