package hk.ljx.fishhub.comment.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.model.bo.CommentBO;
import hk.ljx.fishhub.comment.biz.model.dto.PublishCommentMqDTO;
import hk.ljx.fishhub.comment.biz.rpc.NoteRpcService;
import hk.ljx.fishhub.comment.biz.service.CommentChangedLocalHandler;
import hk.ljx.fishhub.note.api.NoteWriteAccessCheckReqDTO;
import hk.ljx.framework.mq.tx.TransactionalMqSender;
import hk.ljx.framework.mq.tx.TxJournalStore;
import hk.ljx.framework.mq.tx.TxLocalTransaction;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Comment2DBConsumerTest {

    @Mock
    private CommentDOMapper commentDOMapper;
    @Mock
    private NoteRpcService noteRpcService;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private TransactionalMqSender transactionalMqSender;
    @Mock
    private TxJournalStore txJournalStore;
    @Mock
    private CommentChangedLocalHandler commentChangedLocalHandler;
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

        boolean success = consumer.consume(List.of(message));

        assertEquals(true, success);
        verify(noteRpcService).findWritableNoteAccesses(anyList());
        verify(commentDOMapper, never()).batchInsert(anyList());
    }

    @Test
    void shouldBatchInsertWholeBatchInSingleMapperCall() {
        when(commentDOMapper.selectByCommentIds(anyList())).thenReturn(Collections.emptyList());
        List<NoteWriteAccessCheckReqDTO> accesses = List.of(
                NoteWriteAccessCheckReqDTO.builder().noteId(2L).userId(3L).build(),
                NoteWriteAccessCheckReqDTO.builder().noteId(4L).userId(5L).build());
        when(noteRpcService.findWritableNoteAccesses(anyList())).thenReturn(accesses);
        when(commentDOMapper.batchInsert(anyList())).thenReturn(2);
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<Object> callback = inv.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        doAnswer(inv -> {
            TxLocalTransaction action = inv.getArgument(2);
            action.execute("test-txid");
            return null;
        }).when(transactionalMqSender)
                .sendInTransaction(eq(MQConstants.TOPIC_COMMENT_CHANGED), anyString(), any());

        MessageExt first = message(1L, 2L, 3L, "第一条评论");
        MessageExt second = message(2L, 4L, 5L, "第二条评论");

        boolean success = consumer.consume(List.of(first, second));

        assertEquals(true, success);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CommentBO>> captor = ArgumentCaptor.forClass(List.class);
        // 整批 2 条只调 1 次 batchInsert（1 个事务、1 条多行 SQL）
        verify(commentDOMapper, times(1)).batchInsert(captor.capture());
        assertEquals(2, captor.getValue().size());
        verify(txJournalStore).record("test-txid");
    }

    private MessageExt message(long commentId, long noteId, long creatorId, String content) {
        MessageExt message = new MessageExt();
        message.setBody(JsonUtils.toJsonString(PublishCommentMqDTO.builder()
                .commentId(commentId)
                .noteId(noteId)
                .creatorId(creatorId)
                .createTime(LocalDateTime.of(2026, 8, 16, 12, 0))
                .content(content)
                .build()).getBytes(StandardCharsets.UTF_8));
        return message;
    }

    @Test
    void shouldDiscardBatchWhenAllReplyTargetsAreInvalid() {
        when(commentDOMapper.selectByCommentIds(eq(List.of(10L)))).thenReturn(Collections.emptyList());
        when(noteRpcService.findWritableNoteAccesses(anyList()))
                .thenReturn(List.of(NoteWriteAccessCheckReqDTO.builder().noteId(2L).userId(3L).build()));

        MessageExt message = new MessageExt();
        message.setReconsumeTimes(4); // 超过重试阈值，直接校验丢弃
        message.setBody(JsonUtils.toJsonString(PublishCommentMqDTO.builder()
                .commentId(10L)
                .noteId(2L)
                .creatorId(3L)
                .replyCommentId(999L)
                .createTime(LocalDateTime.of(2026, 8, 16, 12, 0))
                .content("无效回复")
                .build()).getBytes(StandardCharsets.UTF_8));

        when(commentDOMapper.selectByCommentIds(eq(List.of(999L)))).thenReturn(Collections.emptyList());

        boolean success = consumer.consume(List.of(message));

        assertEquals(true, success);
        verify(commentDOMapper, never()).batchInsert(anyList());
        verify(transactionalMqSender, never()).sendInTransaction(anyString(), anyString(), any());
    }
}
