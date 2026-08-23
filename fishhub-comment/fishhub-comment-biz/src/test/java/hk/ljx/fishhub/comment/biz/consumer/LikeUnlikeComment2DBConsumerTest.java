package hk.ljx.fishhub.comment.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.enums.CommentLevelEnum;
import hk.ljx.fishhub.comment.biz.enums.LikeUnlikeCommentTypeEnum;
import hk.ljx.fishhub.comment.biz.model.dto.LikeUnlikeCommentMqDTO;
import hk.ljx.fishhub.comment.biz.rpc.NoteRpcService;
import hk.ljx.fishhub.comment.biz.service.CommentLikePersistenceService;
import hk.ljx.fishhub.comment.biz.service.CommentLikeRealtimeService;
import hk.ljx.fishhub.note.api.NoteWriteAccessCheckReqDTO;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LikeUnlikeComment2DBConsumerTest {

    @Mock
    private CommentLikePersistenceService persistenceService;
    @Mock
    private CommentDOMapper commentDOMapper;
    @Mock
    private NoteRpcService noteRpcService;
    @Mock
    private CommentLikeRealtimeService commentLikeRealtimeService;
    @Mock
    private RocketMQTemplate rocketMQTemplate;

    private LikeUnlikeComment2DBConsumer consumer;

    @BeforeEach
    void setUp() throws Exception {
        consumer = new LikeUnlikeComment2DBConsumer(
                persistenceService,
                commentDOMapper,
                noteRpcService,
                commentLikeRealtimeService,
                rocketMQTemplate,
                null
        );
    }

    private MessageExt createMessage(Long commentId, Long userId, Integer type) {
        MessageExt message = new MessageExt();
        LikeUnlikeCommentMqDTO dto = LikeUnlikeCommentMqDTO.builder()
                .commentId(commentId)
                .userId(userId)
                .type(type)
                .createTime(LocalDateTime.now())
                .build();
        message.setBody(JsonUtils.toJsonString(dto).getBytes(StandardCharsets.UTF_8));
        return message;
    }

    @Test
    void shouldAllowUnlikePersistWhenCommentAlreadyDeletedInDb() {
        // comment 100 已在 t_comment 中删除 (查出 emptyList)
        when(commentDOMapper.selectNoteIdsByCommentIds(eq(List.of(100L)))).thenReturn(Collections.emptyList());
        when(noteRpcService.findWritableNoteAccesses(anyList())).thenReturn(Collections.emptyList());
        when(persistenceService.applyBatch(anyList())).thenReturn(List.of(100L));

        MessageExt message = createMessage(100L, 2L, LikeUnlikeCommentTypeEnum.UNLIKE.getCode());
        boolean success = ReflectionTestUtils.invokeMethod(consumer, "consumeBatch", List.of(message));

        assertTrue(success);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LikeUnlikeCommentMqDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(persistenceService, times(1)).applyBatch(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(100L, captor.getValue().get(0).getCommentId());
        assertEquals(LikeUnlikeCommentTypeEnum.UNLIKE.getCode(), captor.getValue().get(0).getType());

        // 回滚不触发
        verify(commentLikeRealtimeService, never()).markUnliked(any(), any());
        // comment 已不存在，热度更新不向已删评论发送
        verify(rocketMQTemplate, never()).asyncSend(anyString(), any(Message.class), any());
    }

    @Test
    void shouldDiscardAndRollbackLikeWhenCommentAlreadyDeletedInDb() {
        // comment 100 已在 t_comment 中删除 (查出 emptyList)
        when(commentDOMapper.selectNoteIdsByCommentIds(eq(List.of(100L)))).thenReturn(Collections.emptyList());
        when(noteRpcService.findWritableNoteAccesses(anyList())).thenReturn(Collections.emptyList());

        MessageExt message = createMessage(100L, 2L, LikeUnlikeCommentTypeEnum.LIKE.getCode());
        boolean success = ReflectionTestUtils.invokeMethod(consumer, "consumeBatch", List.of(message));

        assertTrue(success);
        // 点赞被丢弃，不落库
        verify(persistenceService, never()).applyBatch(anyList());
        // 且调用 markUnliked 回滚 Redis 实时点赞状态
        verify(commentLikeRealtimeService, times(1)).markUnliked(2L, 100L);
    }

    @Test
    void shouldDiscardAndRollbackLikeWhenNoteNotWritable() {
        // comment 100 存在，但所属 note 50 不可写
        CommentDO commentDO = CommentDO.builder().id(100L).noteId(50L).build();
        when(commentDOMapper.selectNoteIdsByCommentIds(eq(List.of(100L)))).thenReturn(List.of(commentDO));
        when(noteRpcService.findWritableNoteAccesses(anyList())).thenReturn(Collections.emptyList());

        MessageExt message = createMessage(100L, 2L, LikeUnlikeCommentTypeEnum.LIKE.getCode());
        boolean success = ReflectionTestUtils.invokeMethod(consumer, "consumeBatch", List.of(message));

        assertTrue(success);
        verify(persistenceService, never()).applyBatch(anyList());
        verify(commentLikeRealtimeService, times(1)).markUnliked(2L, 100L);
    }

    @Test
    void shouldPersistFirstLevelLikeWhenNoteWritable() {
        CommentDO commentDO = CommentDO.builder()
                .id(100L)
                .noteId(50L)
                .level(CommentLevelEnum.ONE.getCode())
                .build();
        when(commentDOMapper.selectNoteIdsByCommentIds(eq(List.of(100L)))).thenReturn(List.of(commentDO));
        when(noteRpcService.findWritableNoteAccesses(anyList()))
                .thenReturn(List.of(NoteWriteAccessCheckReqDTO.builder().noteId(50L).userId(2L).build()));
        when(persistenceService.applyBatch(anyList())).thenReturn(List.of(100L));

        MessageExt message = createMessage(100L, 2L, LikeUnlikeCommentTypeEnum.LIKE.getCode());
        boolean success = ReflectionTestUtils.invokeMethod(consumer, "consumeBatch", List.of(message));

        assertTrue(success);
        verify(persistenceService, times(1)).applyBatch(anyList());
        verify(commentLikeRealtimeService, never()).markUnliked(any(), any());
    }

    @Test
    void shouldNotSendHeatForSecondLevelCommentLike() {
        CommentDO commentDO = CommentDO.builder()
                .id(100L)
                .noteId(50L)
                .level(CommentLevelEnum.TWO.getCode())
                .build();
        when(commentDOMapper.selectNoteIdsByCommentIds(eq(List.of(100L)))).thenReturn(List.of(commentDO));
        when(noteRpcService.findWritableNoteAccesses(anyList()))
                .thenReturn(List.of(NoteWriteAccessCheckReqDTO.builder().noteId(50L).userId(2L).build()));
        when(persistenceService.applyBatch(anyList())).thenReturn(List.of(100L));

        MessageExt message = createMessage(100L, 2L, LikeUnlikeCommentTypeEnum.LIKE.getCode());
        boolean success = ReflectionTestUtils.invokeMethod(consumer, "consumeBatch", List.of(message));

        assertTrue(success);
        verify(persistenceService, times(1)).applyBatch(anyList());
        verifyNoInteractions(rocketMQTemplate);
    }
}
