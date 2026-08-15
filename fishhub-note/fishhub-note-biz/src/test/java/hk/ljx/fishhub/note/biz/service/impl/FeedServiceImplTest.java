package hk.ljx.fishhub.note.biz.service.impl;

import hk.ljx.fishhub.count.dto.FindNoteCountsByIdRspDTO;
import hk.ljx.fishhub.note.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteDO;
import hk.ljx.fishhub.note.biz.domain.dataobject.TopicDO;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteDOMapper;
import hk.ljx.fishhub.note.biz.domain.mapper.TopicDOMapper;
import hk.ljx.fishhub.note.biz.model.vo.FindDiscoverNoteListReqVO;
import hk.ljx.fishhub.note.biz.model.vo.FindTopicListReqVO;
import hk.ljx.fishhub.note.biz.rpc.CountRpcService;
import hk.ljx.fishhub.note.biz.rpc.UserRpcService;
import hk.ljx.fishhub.user.dto.resp.FindUserByIdRspDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedServiceImplTest {

    @Mock
    private NoteDOMapper noteDOMapper;
    @Mock
    private TopicDOMapper topicDOMapper;
    @Mock
    private UserRpcService userRpcService;
    @Mock
    private CountRpcService countRpcService;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @InjectMocks
    private FeedServiceImpl service;

    @Test
    void shouldRequestCountsOnceWhenCursorPageCacheMisses() {
        String pageKey = "feed:discover:cursor:v1:channel:0:cursor:first";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("feed:discover:version")).thenReturn("v1");
        when(valueOperations.get(pageKey)).thenReturn(null);
        when(noteDOMapper.selectDiscoverPageListByCursor(null, null, 11L)).thenReturn(List.of(
                NoteDO.builder().id(101L).creatorId(10L).title("标题").build()));
        when(userRpcService.findByIds(List.of(10L))).thenReturn(List.of(
                FindUserByIdRspDTO.builder().id(10L).nickName("作者").build()));
        when(countRpcService.findByNoteIds(List.of(101L))).thenReturn(List.of(
                FindNoteCountsByIdRspDTO.builder().noteId(101L).likeTotal(3L).build()));
        FindDiscoverNoteListReqVO request = new FindDiscoverNoteListReqVO();
        request.setCursor(0L);

        var response = service.findDiscoverNoteList(request);

        assertEquals(1, response.getData().size());
        assertEquals("3", response.getData().get(0).getLikeTotal());
        verify(countRpcService, times(1)).findByNoteIds(List.of(101L));
    }

    @Test
    void shouldDeleteCorruptedCursorPageCacheAndReloadIt() {
        String pageKey = "feed:discover:cursor:v1:channel:0:cursor:first";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("feed:discover:version")).thenReturn("v1");
        when(valueOperations.get(pageKey)).thenReturn("{");
        when(noteDOMapper.selectDiscoverPageListByCursor(null, null, 11L)).thenReturn(List.of(
                NoteDO.builder().id(101L).creatorId(10L).title("标题").build()));
        when(userRpcService.findByIds(List.of(10L))).thenReturn(List.of(
                FindUserByIdRspDTO.builder().id(10L).nickName("作者").build()));
        when(countRpcService.findByNoteIds(List.of(101L))).thenReturn(List.of(
                FindNoteCountsByIdRspDTO.builder().noteId(101L).likeTotal(3L).build()));
        FindDiscoverNoteListReqVO request = new FindDiscoverNoteListReqVO();
        request.setCursor(0L);

        service.findDiscoverNoteList(request);

        verify(redisTemplate, atLeastOnce()).delete(pageKey);
    }

    @Test
    void shouldFallBackToMySqlWhenDiscoverRedisIsUnavailable() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("feed:discover:version")).thenThrow(new IllegalStateException("redis unavailable"));
        when(noteDOMapper.selectDiscoverPageListByCursor(null, null, 11L)).thenReturn(List.of(
                NoteDO.builder().id(101L).creatorId(10L).title("标题").build()));
        when(userRpcService.findByIds(List.of(10L))).thenReturn(List.of(
                FindUserByIdRspDTO.builder().id(10L).nickName("作者").build()));
        when(countRpcService.findByNoteIds(List.of(101L))).thenReturn(List.of(
                FindNoteCountsByIdRspDTO.builder().noteId(101L).likeTotal(3L).build()));
        FindDiscoverNoteListReqVO request = new FindDiscoverNoteListReqVO();
        request.setCursor(0L);

        var response = service.findDiscoverNoteList(request);

        assertEquals(1, response.getData().size());
        verify(noteDOMapper).selectDiscoverPageListByCursor(null, null, 11L);
    }

    @Test
    void shouldFallBackToMySqlWhenTopicRedisIsUnavailable() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisKeyConstants.activeTopicSnapshotKey()))
                .thenThrow(new IllegalStateException("redis unavailable"));
        when(topicDOMapper.selectAllEnabled()).thenReturn(List.of(TopicDO.builder().id(1L).name("Java").build()));
        FindTopicListReqVO request = new FindTopicListReqVO();
        request.setKeyword("java");

        var response = service.findTopicList(request);

        assertEquals(1, response.getData().size());
        verify(topicDOMapper).selectAllEnabled();
    }

    @Test
    void shouldUseDoubleCheckAfterAcquiringDiscoverPageRebuildLock() {
        String pageKey = "feed:discover:cursor:v1:channel:0:cursor:first";
        String lockKey = "lock:feed:discover:cursor:v1:channel:0:cursor:first";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("feed:discover:version")).thenReturn("v1");
        when(valueOperations.get(pageKey)).thenReturn(null, "{\"notes\":[],\"nextCursor\":null}");
        when(valueOperations.setIfAbsent(eq(lockKey), any(), eq(5L), eq(java.util.concurrent.TimeUnit.SECONDS)))
                .thenReturn(true);
        FindDiscoverNoteListReqVO request = new FindDiscoverNoteListReqVO();
        request.setCursor(0L);

        var response = service.findDiscoverNoteList(request);

        assertEquals(0, response.getData().size());
        verify(noteDOMapper, never()).selectDiscoverPageListByCursor(any(), any(), anyLong());
    }

    @Test
    void shouldNotRetryMySqlWhenDiscoverPageRebuildFails() {
        String pageKey = "feed:discover:cursor:v1:channel:0:cursor:first";
        String lockKey = "lock:feed:discover:cursor:v1:channel:0:cursor:first";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("feed:discover:version")).thenReturn("v1");
        when(valueOperations.get(pageKey)).thenReturn(null);
        when(valueOperations.setIfAbsent(eq(lockKey), any(), eq(5L), eq(java.util.concurrent.TimeUnit.SECONDS)))
                .thenReturn(true);
        when(noteDOMapper.selectDiscoverPageListByCursor(null, null, 11L))
                .thenThrow(new IllegalStateException("mysql unavailable"));
        FindDiscoverNoteListReqVO request = new FindDiscoverNoteListReqVO();
        request.setCursor(0L);

        assertThrows(IllegalStateException.class, () -> service.findDiscoverNoteList(request));

        verify(noteDOMapper, times(1)).selectDiscoverPageListByCursor(null, null, 11L);
    }
}
