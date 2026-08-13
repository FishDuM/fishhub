package hk.ljx.fishhub.note.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteDO;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteDOMapper;
import hk.ljx.fishhub.note.biz.model.dto.NoteContentTaskMqDTO;
import hk.ljx.fishhub.note.biz.rpc.KeyValueRpcService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncNoteContentConsumerTest {

    @Mock
    private KeyValueRpcService keyValueRpcService;
    @Mock
    private NoteDOMapper noteDOMapper;
    @InjectMocks
    private SyncNoteContentConsumer consumer;

    @Test
    void shouldWriteContentWithStableTaskPayload() {
        when(keyValueRpcService.saveNoteContent("uuid", "正文")).thenReturn(true);
        when(noteDOMapper.selectByPrimaryKey(1L)).thenReturn(NoteDO.builder().id(1L).contentUuid("uuid").build());

        consumer.onMessage(JsonUtils.toJsonString(NoteContentTaskMqDTO.builder()
                .noteId(1L)
                .contentUuid("uuid")
                .content("正文")
                .type("UPSERT")
                .build()));

        verify(keyValueRpcService).saveNoteContent("uuid", "正文");
    }

    @Test
    void shouldDeleteContentWithStableTaskPayload() {
        when(keyValueRpcService.deleteNoteContent("uuid")).thenReturn(true);

        consumer.onMessage(JsonUtils.toJsonString(NoteContentTaskMqDTO.builder()
                .noteId(1L)
                .contentUuid("uuid")
                .type("DELETE")
                .build()));

        verify(keyValueRpcService).deleteNoteContent("uuid");
    }

    @Test
    void shouldCleanUpAnUpsertTaskForDeletedOrReplacedContent() {
        when(noteDOMapper.selectByPrimaryKey(1L)).thenReturn(null);
        when(keyValueRpcService.deleteNoteContent("uuid")).thenReturn(true);

        consumer.onMessage(JsonUtils.toJsonString(NoteContentTaskMqDTO.builder()
                .noteId(1L)
                .contentUuid("uuid")
                .content("正文")
                .type("UPSERT")
                .build()));

        verify(keyValueRpcService, never()).saveNoteContent("uuid", "正文");
        verify(keyValueRpcService).deleteNoteContent("uuid");
    }

    @Test
    void shouldCleanUpContentDeletedWhileWriting() {
        when(noteDOMapper.selectByPrimaryKey(1L))
                .thenReturn(NoteDO.builder().id(1L).contentUuid("uuid").build(), null);
        when(keyValueRpcService.saveNoteContent("uuid", "正文")).thenReturn(true);
        when(keyValueRpcService.deleteNoteContent("uuid")).thenReturn(true);

        consumer.onMessage(JsonUtils.toJsonString(NoteContentTaskMqDTO.builder()
                .noteId(1L)
                .contentUuid("uuid")
                .content("正文")
                .type("UPSERT")
                .build()));

        verify(keyValueRpcService).saveNoteContent("uuid", "正文");
        verify(keyValueRpcService).deleteNoteContent("uuid");
    }
}
