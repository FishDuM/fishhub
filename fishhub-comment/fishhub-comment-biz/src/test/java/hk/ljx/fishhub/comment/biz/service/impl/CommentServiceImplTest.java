package hk.ljx.fishhub.comment.biz.service.impl;

import hk.ljx.framework.biz.context.holder.LoginUserContextHolder;
import hk.ljx.framework.common.exception.BizException;
import hk.ljx.fishhub.comment.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.model.vo.FindCommentPageListReqVO;
import hk.ljx.fishhub.comment.biz.model.vo.LikeCommentReqVO;
import hk.ljx.fishhub.comment.biz.model.vo.UnLikeCommentReqVO;
import hk.ljx.fishhub.comment.biz.rpc.NoteRpcService;
import hk.ljx.fishhub.comment.biz.service.CommentLikeRealtimeService;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock rebuildLock;
    @Mock
    private CommentLikeRealtimeService commentLikeRealtimeService;
    @Mock
    private RocketMQTemplate rocketMQTemplate;
    @InjectMocks
    private CommentServiceImpl service;

    @Test
    void shouldDoubleCheckOneLevelCommentTotalAfterAcquiringRebuildLock() throws InterruptedException {
        Long noteId = 100L;
        String cacheKey = "cache:comment:one-level-total:" + noteId + ":v:0";
        String versionKey = "version:comment:one-level-total:" + noteId;
        String lockKey = "lock:comment:one-level-total:" + noteId;
        when(noteRpcService.isAccessible(noteId)).thenReturn(true);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(versionKey)).thenReturn("0");
        when(valueOperations.get(cacheKey)).thenReturn(null, "0");
        when(redissonClient.getLock(lockKey)).thenReturn(rebuildLock);
        when(rebuildLock.tryLock(0, 2L, TimeUnit.SECONDS)).thenReturn(true);
        FindCommentPageListReqVO request = FindCommentPageListReqVO.builder().noteId(noteId).pageNo(1).build();

        var response = service.findCommentPageList(request);

        verify(commentDOMapper, times(0)).selectOneLevelCountByNoteId(noteId);
        org.junit.jupiter.api.Assertions.assertEquals(0L, response.getTotalCount());
    }

    @Test
    void shouldRebuildCommentListZSetOnlyByLockWinner() throws InterruptedException {
        String key = RedisKeyConstants.buildCommentListKey(100L);
        String lockKey = RedisKeyConstants.buildCommentListRebuildLockKey(100L);
        when(stringRedisTemplate.hasKey(key)).thenReturn(false, false);
        when(redissonClient.getLock(lockKey)).thenReturn(rebuildLock);
        when(rebuildLock.tryLock(0, 5L, TimeUnit.SECONDS)).thenReturn(true);
        when(rebuildLock.isHeldByCurrentThread()).thenReturn(true);
        when(commentDOMapper.selectHeatComments(100L)).thenReturn(List.of());

        ReflectionTestUtils.invokeMethod(service, "rebuildCommentListZSetWithLock", key, 100L);

        verify(commentDOMapper, times(1)).selectHeatComments(100L);
        verify(rebuildLock).unlock();
    }

    @Test
    void shouldSkipRebuildWhenCommentListLockNotAcquired() throws InterruptedException {
        String key = RedisKeyConstants.buildCommentListKey(100L);
        String lockKey = RedisKeyConstants.buildCommentListRebuildLockKey(100L);
        when(stringRedisTemplate.hasKey(key)).thenReturn(false, false, false);
        when(redissonClient.getLock(lockKey)).thenReturn(rebuildLock);
        when(rebuildLock.tryLock(0, 5L, TimeUnit.SECONDS)).thenReturn(false);

        ReflectionTestUtils.invokeMethod(service, "rebuildCommentListZSetWithLock", key, 100L);

        verify(commentDOMapper, never()).selectHeatComments(100L);
        verify(rebuildLock, never()).unlock();
    }

    @Test
    void shouldNotRetryMySqlWhenOneLevelCommentCountQueryFails() throws InterruptedException {
        Long noteId = 100L;
        String cacheKey = "cache:comment:one-level-total:" + noteId + ":v:0";
        String versionKey = "version:comment:one-level-total:" + noteId;
        String lockKey = "lock:comment:one-level-total:" + noteId;
        when(noteRpcService.isAccessible(noteId)).thenReturn(true);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(versionKey)).thenReturn("0");
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(redissonClient.getLock(lockKey)).thenReturn(rebuildLock);
        when(rebuildLock.tryLock(0, 2L, TimeUnit.SECONDS)).thenReturn(true);
        when(commentDOMapper.selectOneLevelCountByNoteId(noteId))
                .thenThrow(new IllegalStateException("mysql unavailable"));
        FindCommentPageListReqVO request = FindCommentPageListReqVO.builder().noteId(noteId).pageNo(1).build();

        assertThrows(IllegalStateException.class, () -> service.findCommentPageList(request));

        verify(commentDOMapper, times(1)).selectOneLevelCountByNoteId(noteId);
    }

    @AfterEach
    void tearDown() {
        LoginUserContextHolder.remove();
    }

    @Test
    void likeCommentShouldRejectWhenAlreadyLiked() {
        LoginUserContextHolder.setUserId(2L);
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
                UnLikeCommentReqVO.builder().commentId(100L).build()));

        verify(rocketMQTemplate, never()).syncSendOrderly(anyString(), any(Message.class), anyString());
        verify(commentLikeRealtimeService, never()).markUnliked(anyLong(), anyLong());
    }
}
