package hk.ljx.fishhub.comment.biz.service.impl;

import hk.ljx.framework.biz.context.holder.LoginUserContextHolder;
import hk.ljx.framework.common.exception.BizException;
import hk.ljx.framework.common.response.PageResponse;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.model.vo.FindCommentPageListReqVO;
import hk.ljx.fishhub.comment.biz.model.vo.LikeCommentReqVO;
import hk.ljx.fishhub.comment.biz.model.vo.UnlikeCommentReqVO;
import hk.ljx.fishhub.comment.biz.rpc.NoteRpcService;
import hk.ljx.fishhub.comment.biz.model.vo.FindCommentItemRspVO;
import hk.ljx.fishhub.comment.biz.service.CommentCacheService;
import hk.ljx.fishhub.comment.biz.service.CommentLikeRealtimeService;
import hk.ljx.fishhub.user.client.UserClient;
import hk.ljx.fishhub.user.dto.rsp.FindUserByIdRspDTO;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    @Mock
    private NoteRpcService noteRpcService;
    @Mock
    private CommentDOMapper commentDOMapper;
    @Mock
    private CommentCacheService commentCacheService;
    @Mock
    private UserClient userClient;
    @Mock
    private CommentLikeRealtimeService commentLikeRealtimeService;
    @Mock
    private RocketMQTemplate rocketMQTemplate;
    @Mock
    private org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @InjectMocks
    private CommentServiceImpl service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        var pageCache = (com.github.benmanes.caffeine.cache.Cache<?, ?>) ReflectionTestUtils.getField(CommentServiceImpl.class, "COMMENT_PAGE_LOCAL_CACHE");
        if (pageCache != null) pageCache.invalidateAll();
        var childCache = (com.github.benmanes.caffeine.cache.Cache<?, ?>) ReflectionTestUtils.getField(CommentServiceImpl.class, "CHILD_COMMENT_PAGE_LOCAL_CACHE");
        if (childCache != null) childCache.invalidateAll();
    }

    @Test
    void shouldReturnEmptyPageWhenTotalCountIsZero() {
        Long noteId = 100L;
        when(noteRpcService.isAccessible(noteId)).thenReturn(true);
        when(commentCacheService.getOneLevelCommentTotal(eq(noteId), any())).thenReturn(0L);
        FindCommentPageListReqVO request = FindCommentPageListReqVO.builder().noteId(noteId).pageNo(1).build();

        var response = service.findCommentPageList(request);

        assertEquals(0L, response.getTotalCount());
        assertEquals(0, response.getData().size());
        verify(commentCacheService, never()).getCommentIdsByZSet(anyLong(), anyLong(), anyLong());
    }

    @Test
    void shouldRebuildCommentListZSetWhenMissingAndLockAcquired() {
        Long noteId = 101L;
        when(noteRpcService.isAccessible(noteId)).thenReturn(true);
        when(commentCacheService.getOneLevelCommentTotal(eq(noteId), any())).thenReturn(10L);
        when(commentCacheService.hasCommentListZSet(noteId)).thenReturn(false, false);
        when(commentCacheService.tryLockCommentListRebuild(noteId)).thenReturn(true);
        when(commentDOMapper.selectHeatComments(noteId)).thenReturn(List.of());
        when(commentDOMapper.selectPageList(noteId, 0, 10)).thenReturn(List.of());

        FindCommentPageListReqVO request = FindCommentPageListReqVO.builder().noteId(noteId).pageNo(1).build();
        service.findCommentPageList(request);

        verify(commentCacheService).tryLockCommentListRebuild(noteId);
        verify(commentDOMapper).selectHeatComments(noteId);
        verify(commentCacheService).unlockCommentListRebuild(noteId);
    }

    @Test
    void shouldSkipRebuildWhenCommentListLockNotAcquired() {
        Long noteId = 102L;
        when(noteRpcService.isAccessible(noteId)).thenReturn(true);
        when(commentCacheService.getOneLevelCommentTotal(eq(noteId), any())).thenReturn(10L);
        when(commentCacheService.hasCommentListZSet(noteId)).thenReturn(false);
        when(commentCacheService.tryLockCommentListRebuild(noteId)).thenReturn(false);
        when(commentDOMapper.selectPageList(noteId, 0, 10)).thenReturn(List.of());

        FindCommentPageListReqVO request = FindCommentPageListReqVO.builder().noteId(noteId).pageNo(1).build();
        service.findCommentPageList(request);

        verify(commentDOMapper, never()).selectHeatComments(noteId);
        verify(commentCacheService, never()).unlockCommentListRebuild(noteId);
    }

    @AfterEach
    void tearDown() {
        LoginUserContextHolder.remove();
    }

    @Test
    void publishCommentShouldRejectWhenNoteNotWritable() {
        LoginUserContextHolder.setUserId(2L);
        when(noteRpcService.isWritable(50L, 2L)).thenReturn(false);

        hk.ljx.fishhub.comment.biz.model.vo.PublishCommentReqVO req = new hk.ljx.fishhub.comment.biz.model.vo.PublishCommentReqVO();
        req.setNoteId(50L);
        req.setContent("hello");

        assertThrows(BizException.class, () -> service.publishComment(req));
    }

    @Test
    void likeCommentShouldRejectWhenAlreadyLiked() {
        LoginUserContextHolder.setUserId(2L);
        when(commentDOMapper.selectByPrimaryKey(100L))
                .thenReturn(hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO.builder().id(100L).noteId(50L).build());
        when(noteRpcService.isWritable(50L, 2L)).thenReturn(true);
        when(commentLikeRealtimeService.containsLiked(2L, 100L)).thenReturn(true);

        assertThrows(BizException.class, () -> service.likeComment(
                LikeCommentReqVO.builder().commentId(100L).build()));

        // 已点赞拒绝后不发 MQ 且不更新状态
        verify(rocketMQTemplate, never()).syncSendOrderly(anyString(), any(Message.class), anyString());
        verify(commentLikeRealtimeService, never()).markLiked(anyLong(), anyLong());
    }

    @Test
    void unlikeCommentShouldRejectWhenNotLiked() {
        LoginUserContextHolder.setUserId(2L);
        when(commentLikeRealtimeService.containsLiked(2L, 100L)).thenReturn(false);

        assertThrows(BizException.class, () -> service.unlikeComment(
                UnlikeCommentReqVO.builder().commentId(100L).build()));

        verify(rocketMQTemplate, never()).syncSendOrderly(anyString(), any(Message.class), anyString());
        verify(commentLikeRealtimeService, never()).markUnliked(anyLong(), anyLong());
        verify(commentDOMapper, never()).selectByPrimaryKey(anyLong());
    }

    @Test
    void unlikeCommentShouldSucceedEvenWhenCommentOrNoteDeletedInDb() {
        LoginUserContextHolder.setUserId(2L);
        when(commentLikeRealtimeService.containsLiked(2L, 100L)).thenReturn(true);

        var response = service.unlikeComment(
                UnlikeCommentReqVO.builder().commentId(100L).build());

        org.junit.jupiter.api.Assertions.assertTrue(response.isSuccess());
        verify(commentDOMapper, never()).selectByPrimaryKey(anyLong());
        verify(noteRpcService, never()).isWritable(anyLong(), anyLong());
        verify(commentLikeRealtimeService).markUnliked(2L, 100L);
    }

    @Test
    void likeCommentShouldUpdateRealtimeStateBeforeMqSend() {
        LoginUserContextHolder.setUserId(2L);
        when(commentDOMapper.selectByPrimaryKey(100L))
                .thenReturn(hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO.builder().id(100L).noteId(50L).build());
        when(noteRpcService.isWritable(50L, 2L)).thenReturn(true);
        when(commentLikeRealtimeService.containsLiked(2L, 100L)).thenReturn(false);

        service.likeComment(LikeCommentReqVO.builder().commentId(100L).build());

        InOrder inOrder = inOrder(commentLikeRealtimeService, rocketMQTemplate);
        inOrder.verify(commentLikeRealtimeService).markLiked(2L, 100L);
        inOrder.verify(rocketMQTemplate).syncSendOrderly(
                anyString(), any(Object.class), anyString());
    }

    @Test
    void shouldAssembleCommentItemVOWithFirstReply() {
        hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO parentDO = hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO.builder()
                .id(1L)
                .userId(10L)
                .contentUuid("uuid-1")
                .firstReplyCommentId(2L)
                .likeTotal(5L)
                .childCommentTotal(1L)
                .heat(3.8)
                .createTime(java.time.LocalDateTime.now())
                .isContentEmpty(false)
                .build();

        hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO replyDO = hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO.builder()
                .id(2L)
                .userId(20L)
                .contentUuid("uuid-2")
                .likeTotal(1L)
                .childCommentTotal(0L)
                .heat(0.7)
                .createTime(java.time.LocalDateTime.now())
                .isContentEmpty(false)
                .build();

        hk.ljx.fishhub.user.dto.rsp.FindUserByIdRspDTO parentUser = new hk.ljx.fishhub.user.dto.rsp.FindUserByIdRspDTO();
        parentUser.setId(10L);
        parentUser.setNickName("Alice");
        parentUser.setAvatar("alice.png");

        FindUserByIdRspDTO replyUser = new FindUserByIdRspDTO();
        replyUser.setId(20L);
        replyUser.setNickName("Bob");
        replyUser.setAvatar("bob.png");

        Map<Long, FindUserByIdRspDTO> userMap = Map.of(10L, parentUser, 20L, replyUser);
        Map<String, String> contentMap = Map.of("uuid-1", "这是一级评论", "uuid-2", "这是首条回复");

        FindCommentItemRspVO parentVO = ReflectionTestUtils.invokeMethod(
                service, "toCommentItemVO", parentDO, userMap, contentMap);
        FindCommentItemRspVO replyVO = ReflectionTestUtils.invokeMethod(
                service, "toCommentItemVO", replyDO, userMap, contentMap);
        parentVO.setFirstReplyComment(replyVO);

        org.junit.jupiter.api.Assertions.assertNotNull(parentVO);
        org.junit.jupiter.api.Assertions.assertEquals("Alice", parentVO.getNickname());
        org.junit.jupiter.api.Assertions.assertEquals("这是一级评论", parentVO.getContent());
        org.junit.jupiter.api.Assertions.assertNotNull(parentVO.getFirstReplyComment());
        org.junit.jupiter.api.Assertions.assertEquals(2L, parentVO.getFirstReplyComment().getCommentId());
        org.junit.jupiter.api.Assertions.assertEquals("Bob", parentVO.getFirstReplyComment().getNickname());
        org.junit.jupiter.api.Assertions.assertEquals("这是首条回复", parentVO.getFirstReplyComment().getContent());
    }
}
