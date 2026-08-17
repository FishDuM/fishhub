package hk.ljx.fishhub.note.biz.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import hk.ljx.fishhub.count.dto.FindNoteCountsByIdRspDTO;
import hk.ljx.fishhub.note.api.NoteWriteAccessCheckReqDTO;
import hk.ljx.fishhub.note.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteDO;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteDOMapper;
import hk.ljx.fishhub.note.biz.model.vo.FindNoteDetailReqVO;
import hk.ljx.fishhub.note.biz.rpc.CountRpcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteServiceImplAccessTest {

    @Mock
    private NoteDOMapper noteDOMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock rebuildLock;
    @Mock
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Mock
    private CountRpcService countRpcService;
    @InjectMocks
    private NoteServiceImpl service;

    @BeforeEach
    void clearLocalCache() {
        @SuppressWarnings("unchecked")
        Cache<Long, String> localCache = (Cache<Long, String>) ReflectionTestUtils.getField(NoteServiceImpl.class, "LOCAL_CACHE");
        if (localCache != null) {
            localCache.invalidateAll();
        }
    }

    @Test
    void shouldUseOneBatchQueryWhenAccessSnapshotsAreCold() {
        List<Long> noteIds = List.of(11L, 12L, 13L);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(List.of("note:access:11", "note:access:12", "note:access:13")))
                .thenReturn(Arrays.asList(null, null, null));
        when(noteDOMapper.selectAccessInfosByNoteIds(noteIds)).thenReturn(List.of(
                NoteDO.builder().id(11L).creatorId(1L).visible(0).build(),
                NoteDO.builder().id(12L).creatorId(2L).visible(0).build()));

        var response = service.findAccessibleNoteIds(noteIds);

        assertEquals(List.of(11L, 12L), response.getData());
        verify(noteDOMapper).selectAccessInfosByNoteIds(noteIds);
        verify(noteDOMapper, never()).selectAccessInfoByNoteId(anyLong());
    }

    @Test
    void shouldFallBackToOneBatchQueryWhenRedisIsUnavailable() {
        List<Long> noteIds = List.of(11L, 12L);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(List.of("note:access:11", "note:access:12")))
                .thenThrow(new IllegalStateException("redis unavailable"));
        when(noteDOMapper.selectAccessInfosByNoteIds(noteIds)).thenReturn(List.of(
                NoteDO.builder().id(11L).creatorId(1L).visible(0).build(),
                NoteDO.builder().id(12L).creatorId(2L).visible(0).build()));

        var response = service.findAccessibleNoteIds(noteIds);

        assertEquals(noteIds, response.getData());
        verify(noteDOMapper).selectAccessInfosByNoteIds(noteIds);
    }

    @Test
    void shouldReloadAccessSnapshotWhenCachedJsonIsCorrupted() throws InterruptedException {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("note:access:11")).thenReturn("{", "{");
        when(redissonClient.getLock(RedisKeyConstants.buildNoteAccessRebuildLockKey(11L))).thenReturn(rebuildLock);
        when(rebuildLock.tryLock(0, 2L, TimeUnit.SECONDS)).thenReturn(true);
        when(rebuildLock.isHeldByCurrentThread()).thenReturn(true);
        when(noteDOMapper.selectAccessInfoByNoteId(11L)).thenReturn(
                NoteDO.builder().id(11L).creatorId(1L).visible(0).build());

        var response = service.isAccessible(11L);

        assertEquals(Boolean.TRUE, response.getData());
        verify(noteDOMapper).selectAccessInfoByNoteId(11L);
        verify(valueOperations).set(eq("note:access:11"), anyString(), eq(30L), eq(TimeUnit.SECONDS));
    }

    @Test
    void shouldRebuildAccessSnapshotOnlyByLockWinner() throws InterruptedException {
        String key = RedisKeyConstants.buildNoteAccessKey(11L);
        String lockKey = RedisKeyConstants.buildNoteAccessRebuildLockKey(11L);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenReturn(null, null);
        when(redissonClient.getLock(lockKey)).thenReturn(rebuildLock);
        when(rebuildLock.tryLock(0, 2L, TimeUnit.SECONDS)).thenReturn(true);
        when(rebuildLock.isHeldByCurrentThread()).thenReturn(true);
        when(noteDOMapper.selectAccessInfoByNoteId(11L)).thenReturn(
                NoteDO.builder().id(11L).creatorId(1L).visible(0).revision(1L).build());

        var response = service.isAccessible(11L);

        assertEquals(Boolean.TRUE, response.getData());
        verify(noteDOMapper, times(1)).selectAccessInfoByNoteId(11L);
        verify(valueOperations).set(eq(key), anyString(), eq(30L), eq(TimeUnit.SECONDS));
        verify(rebuildLock).unlock();
    }

    @Test
    void shouldPollThenFallbackToMySqlWhenLockIsNotAcquired() throws InterruptedException {
        String key = RedisKeyConstants.buildNoteAccessKey(11L);
        String lockKey = RedisKeyConstants.buildNoteAccessRebuildLockKey(11L);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        // 首次读 miss + 轮询 3 次 miss（抢不到锁的等待者）
        when(valueOperations.get(key)).thenReturn(null, null, null, null);
        when(redissonClient.getLock(lockKey)).thenReturn(rebuildLock);
        when(rebuildLock.tryLock(0, 2L, TimeUnit.SECONDS)).thenReturn(false);
        when(noteDOMapper.selectAccessInfoByNoteId(11L)).thenReturn(
                NoteDO.builder().id(11L).creatorId(1L).visible(0).revision(1L).build());

        var response = service.isAccessible(11L);

        assertEquals(Boolean.TRUE, response.getData());
        verify(noteDOMapper, times(1)).selectAccessInfoByNoteId(11L);
        // 兜底查库不写回（写回由锁持有者负责）
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        verify(rebuildLock, never()).unlock();
    }

    @Test
    void shouldNotCallCountRpcWhenDetailCacheHasEmbeddedCounts() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisKeyConstants.buildNoteAccessKey(11L)))
                .thenReturn("{\"creatorId\":1,\"visible\":0,\"revision\":1}");
        when(valueOperations.get(RedisKeyConstants.buildNoteDetailKey(11L)))
                .thenReturn("{\"id\":11,\"revision\":1,\"type\":0,\"title\":\"t\",\"likeTotal\":7,\"collectTotal\":8,\"commentTotal\":9}");

        FindNoteDetailReqVO request = new FindNoteDetailReqVO();
        request.setId(11L);
        var response = service.findNoteDetail(request);

        assertEquals(7L, response.getData().getLikeTotal());
        assertEquals(8L, response.getData().getCollectTotal());
        assertEquals(9L, response.getData().getCommentTotal());
        verify(countRpcService, never()).findByNoteIds(any());
    }

    @Test
    void shouldBackfillCountsFromRpcWhenDetailCacheLacksCounts() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisKeyConstants.buildNoteAccessKey(11L)))
                .thenReturn("{\"creatorId\":1,\"visible\":0,\"revision\":1}");
        // 旧格式缓存：计数未内嵌
        when(valueOperations.get(RedisKeyConstants.buildNoteDetailKey(11L)))
                .thenReturn("{\"id\":11,\"revision\":1,\"type\":0,\"title\":\"t\"}");
        when(countRpcService.findByNoteIds(List.of(11L))).thenReturn(List.of(
                FindNoteCountsByIdRspDTO.builder().noteId(11L).likeTotal(5L).collectTotal(6L).commentTotal(7L).build()));

        FindNoteDetailReqVO request = new FindNoteDetailReqVO();
        request.setId(11L);
        var response = service.findNoteDetail(request);

        assertEquals(5L, response.getData().getLikeTotal());
        assertEquals(6L, response.getData().getCollectTotal());
        assertEquals(7L, response.getData().getCommentTotal());
        verify(countRpcService, times(1)).findByNoteIds(List.of(11L));
    }

    @Test
    void shouldBumpDiscoverVersionOnlyForAffectedChannels() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        ReflectionTestUtils.invokeMethod(service, "invalidateNoteRedisCaches", 1L, 11L, new Long[]{1L});

        // 频道 1 发布只 bump 首页 0 与频道 1，不动频道 2
        verify(valueOperations).setIfAbsent(eq(RedisKeyConstants.buildDiscoverFeedVersionKey(0L)), anyString(), eq(30L), eq(TimeUnit.SECONDS));
        verify(valueOperations).setIfAbsent(eq(RedisKeyConstants.buildDiscoverFeedVersionKey(1L)), anyString(), eq(30L), eq(TimeUnit.SECONDS));
        verify(valueOperations, never()).setIfAbsent(eq(RedisKeyConstants.buildDiscoverFeedVersionKey(2L)), anyString(), anyLong(), any(TimeUnit.class));
        // 详情/访问快照/作者列表仍删；版本改为 NX+30s 限频 bump
        verify(stringRedisTemplate).delete(List.of("note:detail:11", "note:access:11", "note:published:list:1"));
    }

    @Test
    void shouldCheckWritableNotesDirectlyFromMySql() {
        List<NoteWriteAccessCheckReqDTO> requests = List.of(
                NoteWriteAccessCheckReqDTO.builder().noteId(11L).userId(101L).build(),
                NoteWriteAccessCheckReqDTO.builder().noteId(12L).userId(102L).build(),
                NoteWriteAccessCheckReqDTO.builder().noteId(13L).userId(103L).build());
        when(noteDOMapper.selectAccessInfosByNoteIds(List.of(11L, 12L, 13L))).thenReturn(List.of(
                NoteDO.builder().id(11L).creatorId(1L).visible(0).build(),
                NoteDO.builder().id(12L).creatorId(102L).visible(1).build(),
                NoteDO.builder().id(13L).creatorId(1L).visible(1).build()));

        var response = service.findWritableNoteAccesses(requests);

        assertEquals(List.of(requests.get(0), requests.get(1)), response.getData());
        verify(noteDOMapper).selectAccessInfosByNoteIds(List.of(11L, 12L, 13L));
        verify(stringRedisTemplate, never()).opsForValue();
    }
}
