package hk.ljx.fishhub.count.biz.service.impl;

import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.count.constant.CountKeyConstants;
import hk.ljx.fishhub.count.biz.domain.dataobject.NoteCountDO;
import hk.ljx.fishhub.count.biz.domain.mapper.NoteCountDOMapper;
import hk.ljx.fishhub.count.dto.FindNoteCountsByIdRspDTO;
import hk.ljx.fishhub.count.dto.FindNoteCountsByIdsReqDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteCountServiceImplTest {

    @Mock
    private NoteCountDOMapper noteCountDOMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ThreadPoolTaskExecutor fishhubTaskExecutor;
    @Mock
    private RedisOperations<String, String> redisOperations;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @InjectMocks
    private NoteCountServiceImpl service;

    @Captor
    private ArgumentCaptor<SessionCallback<Object>> sessionCallbackCaptor;
    @Captor
    private ArgumentCaptor<Runnable> runnableCaptor;

    private FindNoteCountsByIdsReqDTO request(List<Long> noteIds) {
        return FindNoteCountsByIdsReqDTO.builder().noteIds(noteIds).build();
    }

    private void mockReadPipeline(List<Object> result) {
        when(stringRedisTemplate.executePipelined(any(SessionCallback.class))).thenReturn(result);
    }

    @Test
    void shouldNotTouchDbOrExecutorWhenAllCountsHitInRedis() {
        mockReadPipeline(List.of(List.of("5", "3", "2")));

        Response<List<FindNoteCountsByIdRspDTO>> response =
                service.findNotesCountData(request(List.of(1L)));

        assertEquals(1, response.getData().size());
        assertEquals(5L, response.getData().get(0).getLikeTotal());
        assertEquals(3L, response.getData().get(0).getCollectTotal());
        assertEquals(2L, response.getData().get(0).getCommentTotal());
        verify(noteCountDOMapper, never()).selectByNoteIds(any());
        verify(fishhubTaskExecutor, never()).execute(any(Runnable.class));
    }

    @Test
    void shouldFillResponseFromDbAndAsyncWriteBackMissingCounts() {
        mockReadPipeline(Arrays.asList(Arrays.asList("5", "3", null), Arrays.asList(null, null, null)));
        when(noteCountDOMapper.selectByNoteIds(List.of(1L, 2L))).thenReturn(List.of(
                NoteCountDO.builder().noteId(1L).likeTotal(50L).collectTotal(30L).commentTotal(20L).build(),
                NoteCountDO.builder().noteId(2L).likeTotal(60L).collectTotal(40L).commentTotal(10L).build()));

        Response<List<FindNoteCountsByIdRspDTO>> response =
                service.findNotesCountData(request(List.of(1L, 2L)));

        List<FindNoteCountsByIdRspDTO> data = response.getData();
        // 1 号：like/collect 来自缓存，comment 来自 DB；2 号：全来自 DB
        assertEquals(5L, data.get(0).getLikeTotal());
        assertEquals(3L, data.get(0).getCollectTotal());
        assertEquals(20L, data.get(0).getCommentTotal());
        assertEquals(60L, data.get(1).getLikeTotal());
        assertEquals(40L, data.get(1).getCollectTotal());
        assertEquals(10L, data.get(1).getCommentTotal());

        verify(noteCountDOMapper, times(1)).selectByNoteIds(List.of(1L, 2L));
        // 回写被异步提交；手动执行捕获的任务完成写回
        verify(fishhubTaskExecutor).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();
        // 读 pipeline + 写 pipeline 各一次
        verify(stringRedisTemplate, times(2)).executePipelined(any(SessionCallback.class));
    }

    @Test
    void shouldFallBackToSyncWriteBackWhenExecutorRejectsTask() {
        mockReadPipeline(Arrays.asList(Arrays.asList("5", "3", null)));
        when(noteCountDOMapper.selectByNoteIds(List.of(1L))).thenReturn(List.of(
                NoteCountDO.builder().noteId(1L).likeTotal(50L).collectTotal(30L).commentTotal(20L).build()));
        doThrow(new TaskRejectedException("queue full")).when(fishhubTaskExecutor).execute(any(Runnable.class));

        Response<List<FindNoteCountsByIdRspDTO>> response =
                service.findNotesCountData(request(List.of(1L)));

        assertEquals(5L, response.getData().get(0).getLikeTotal());
        assertEquals(3L, response.getData().get(0).getCollectTotal());
        assertEquals(20L, response.getData().get(0).getCommentTotal());
        // 提交失败后降级为同步回写：读 + 写 pipeline 各一次
        verify(stringRedisTemplate, times(2)).executePipelined(any(SessionCallback.class));
    }

    @Test
    void shouldWriteBackOnlyMissingFieldsWhenSnapshotKeepsNulls() {
        // 快照必须保留 null，否则计数全非空会 continue，缓存永远不建
        FindNoteCountsByIdRspDTO dto = FindNoteCountsByIdRspDTO.builder()
                .noteId(1L).likeTotal(5L).collectTotal(3L).commentTotal(null).build();
        NoteCountDO db = NoteCountDO.builder()
                .noteId(1L).likeTotal(50L).collectTotal(30L).commentTotal(20L).build();

        ReflectionTestUtils.invokeMethod(service, "syncNoteHash2Redis", List.of(dto), Map.of(1L, db));
        verify(stringRedisTemplate).executePipelined(sessionCallbackCaptor.capture());
        when(redisOperations.opsForHash()).thenReturn(hashOperations);

        sessionCallbackCaptor.getValue().execute(redisOperations);

        // 只写缺失的 comment 字段，like/collect 已缓存不覆盖
        verify(hashOperations, times(1)).putIfAbsent(
                CountKeyConstants.buildCountNoteKey(1L), CountKeyConstants.FIELD_COMMENT_TOTAL, "20");
        verify(redisOperations).expire(eq(CountKeyConstants.buildCountNoteKey(1L)), anyLong(), any(TimeUnit.class));
    }

    @Test
    void shouldSkipWriteBackWhenSnapshotFieldsAreAllFilled() {
        // 对照组：字段全非空时 syncNoteHash2Redis 会整体跳过
        FindNoteCountsByIdRspDTO dto = FindNoteCountsByIdRspDTO.builder()
                .noteId(1L).likeTotal(5L).collectTotal(3L).commentTotal(20L).build();

        ReflectionTestUtils.invokeMethod(service, "syncNoteHash2Redis", List.of(dto), Map.of());
        verify(stringRedisTemplate).executePipelined(sessionCallbackCaptor.capture());

        sessionCallbackCaptor.getValue().execute(redisOperations);

        verify(hashOperations, never()).putIfAbsent(any(), any(), any());
        verify(redisOperations, never()).expire(any(), anyLong(), any());
    }
}
