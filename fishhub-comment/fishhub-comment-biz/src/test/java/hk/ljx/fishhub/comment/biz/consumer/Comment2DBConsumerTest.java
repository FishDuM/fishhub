package hk.ljx.fishhub.comment.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.model.dto.PublishCommentMqDTO;
import hk.ljx.fishhub.comment.biz.rpc.NoteRpcService;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Comment2DBConsumerTest {

    @Mock
    private CommentDOMapper commentDOMapper;
    @Mock
    private NoteRpcService noteRpcService;
    @InjectMocks
    private Comment2DBConsumer consumer;

    @Test
    void shouldDiscardCommentWhenCurrentNoteWriteAccessIsRejected() {
        when(commentDOMapper.selectByCommentIds(anyList())).thenReturn(Collections.emptyList());
        when(noteRpcService.findWritableNoteAccesses(anyList())).thenReturn(Collections.emptyList());
        MessageExt message = new MessageExt();
        message.setBody(JsonUtils.toJsonString(PublishCommentMqDTO.builder()
                .commentId(1L)
                .noteId(2L)
                .creatorId(3L)
                .createTime(LocalDateTime.of(2026, 8, 16, 12, 0))
                .content("测试评论")
                .build()).getBytes(StandardCharsets.UTF_8));

        ConsumeConcurrentlyStatus status = consumer.consume(List.of(message));

        assertEquals(ConsumeConcurrentlyStatus.CONSUME_SUCCESS, status);
        verify(noteRpcService).findWritableNoteAccesses(anyList());
        verify(commentDOMapper, never()).batchInsert(anyList());
    }
}
