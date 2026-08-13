package hk.ljx.fishhub.comment.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.model.bo.CommentBO;
import hk.ljx.fishhub.comment.biz.model.dto.SyncCommentContentMqDTO;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.rpc.KeyValueRpcService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncCommentContentConsumerTest {

    @Mock
    private KeyValueRpcService keyValueRpcService;
    @Mock
    private CommentDOMapper commentDOMapper;
    @InjectMocks
    private SyncCommentContentConsumer consumer;

    @Test
    void shouldWriteExactlyTheContentIdentifiedByTheTask() {
        LocalDateTime createTime = LocalDateTime.of(2026, 8, 14, 12, 0);
        when(commentDOMapper.selectByPrimaryKey(100L))
                .thenReturn(CommentDO.builder().id(100L).contentUuid("fixed-uuid").build());
        consumer.onMessage(JsonUtils.toJsonString(SyncCommentContentMqDTO.builder()
                .commentId(100L)
                .noteId(10L)
                .createTime(createTime)
                .contentUuid("fixed-uuid")
                .content("评论正文")
                .build()));

        ArgumentCaptor<List<CommentBO>> captor = ArgumentCaptor.forClass(List.class);
        verify(keyValueRpcService).batchSaveCommentContent(captor.capture());
        CommentBO saved = captor.getValue().getFirst();
        assertEquals(10L, saved.getNoteId());
        assertEquals(createTime, saved.getCreateTime());
        assertEquals("fixed-uuid", saved.getContentUuid());
        assertEquals("评论正文", saved.getContent());
    }

    @Test
    void shouldCleanUpAWriteTaskAfterTheCommentIsDeleted() {
        LocalDateTime createTime = LocalDateTime.of(2026, 8, 14, 12, 0);
        when(commentDOMapper.selectByPrimaryKey(100L)).thenReturn(null);

        consumer.onMessage(JsonUtils.toJsonString(SyncCommentContentMqDTO.builder()
                .commentId(100L)
                .noteId(10L)
                .createTime(createTime)
                .contentUuid("fixed-uuid")
                .content("评论正文")
                .build()));

        verify(keyValueRpcService, never()).batchSaveCommentContent(org.mockito.ArgumentMatchers.anyList());
        verify(keyValueRpcService).deleteCommentContent(10L, createTime, "fixed-uuid");
    }

    @Test
    void shouldCleanUpContentDeletedWhileWriting() {
        LocalDateTime createTime = LocalDateTime.of(2026, 8, 14, 12, 0);
        when(commentDOMapper.selectByPrimaryKey(100L))
                .thenReturn(CommentDO.builder().id(100L).contentUuid("fixed-uuid").build(), null);

        consumer.onMessage(JsonUtils.toJsonString(SyncCommentContentMqDTO.builder()
                .commentId(100L)
                .noteId(10L)
                .createTime(createTime)
                .contentUuid("fixed-uuid")
                .content("评论正文")
                .build()));

        verify(keyValueRpcService).batchSaveCommentContent(org.mockito.ArgumentMatchers.anyList());
        verify(keyValueRpcService).deleteCommentContent(10L, createTime, "fixed-uuid");
    }
}
