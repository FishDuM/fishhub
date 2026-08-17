package hk.ljx.fishhub.comment.biz.service.impl;

import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.model.vo.FindCommentPageListReqVO;
import hk.ljx.fishhub.comment.biz.rpc.NoteRpcService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
}
