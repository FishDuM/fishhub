package hk.ljx.fishhub.note.biz.service.impl;

import hk.ljx.fishhub.count.dto.FindNoteCountsByIdRspDTO;
import hk.ljx.fishhub.note.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.note.biz.domain.dataobject.ChannelDO;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteDO;
import hk.ljx.fishhub.note.biz.domain.dataobject.TopicDO;
import hk.ljx.fishhub.note.biz.domain.mapper.ChannelDOMapper;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteDOMapper;
import hk.ljx.fishhub.note.biz.domain.mapper.TopicDOMapper;
import hk.ljx.fishhub.note.biz.model.vo.FindChannelRspVO;
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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.concurrent.TimeUnit;

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
    private ChannelDOMapper channelDOMapper;
    @Mock
    private UserRpcService userRpcService;
    @Mock
    private CountRpcService countRpcService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock rebuildLock;
    @InjectMocks
    private FeedServiceImpl service;

    @Test
    void shouldRequestCountsOnceWhenCursorPageCacheMisses() {
        String pageKey = "feed:discover:cursor:v1:channel:0:cursor:first";
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisKeyConstants.buildDiscoverFeedVersionKey(null))).thenReturn("v1");
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
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisKeyConstants.buildDiscoverFeedVersionKey(null))).thenReturn("v1");
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

        verify(stringRedisTemplate, atLeastOnce()).delete(pageKey);
    }

    @Test
    void shouldFallBackToMySqlWhenDiscoverRedisIsUnavailable() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisKeyConstants.buildDiscoverFeedVersionKey(null))).thenThrow(new IllegalStateException("redis unavailable"));
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
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
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
    void shouldUsePerChannelFeedVersionKey() {
        String channelPageKey = "feed:discover:cursor:v2:channel:1:cursor:first";
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisKeyConstants.buildDiscoverFeedVersionKey(1L))).thenReturn("v2");
        when(valueOperations.get(channelPageKey))
                .thenReturn("{\"notes\":[],\"nextCursor\":null}");
        FindDiscoverNoteListReqVO request = new FindDiscoverNoteListReqVO();
        request.setCursor(0L);
        request.setChannelId(1L);

        var response = service.findDiscoverNoteList(request);

        assertEquals(0, response.getData().size());
        // 频道 1 版本 key 独立于首页
        verify(valueOperations).get(RedisKeyConstants.buildDiscoverFeedVersionKey(1L));
        verify(valueOperations).get(channelPageKey);
    }

    @Test
    void shouldNotCallCountRpcWhenDiscoverSnapshotHasEmbeddedCounts() {
        String pageKey = "feed:discover:cursor:v1:channel:0:cursor:first";
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisKeyConstants.buildDiscoverFeedVersionKey(null))).thenReturn("v1");
        when(valueOperations.get(pageKey))
                .thenReturn("{\"notes\":[{\"noteId\":101,\"type\":0,\"title\":\"标题\",\"likeTotal\":\"5\",\"isLiked\":false}],\"nextCursor\":null}");
        FindDiscoverNoteListReqVO request = new FindDiscoverNoteListReqVO();
        request.setCursor(0L);

        var response = service.findDiscoverNoteList(request);

        assertEquals(1, response.getData().size());
        assertEquals("5", response.getData().get(0).getLikeTotal());
        // 快照命中计数已内嵌，免 count Feign
        verify(countRpcService, never()).findByNoteIds(any());
    }

    @Test
    void shouldCacheChannelListAfterFirstQuery() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisKeyConstants.activeChannelSnapshotKey())).thenReturn(null);
        when(channelDOMapper.selectAllEnabled()).thenReturn(List.of(
                ChannelDO.builder().id(1L).name("Java").build(),
                ChannelDO.builder().id(2L).name("前端").build()));

        var first = service.findChannelList();
        assertEquals(2, first.getData().size());
        verify(channelDOMapper, times(1)).selectAllEnabled();

        var second = service.findChannelList();
        assertEquals(2, second.getData().size());
        assertEquals("Java", second.getData().get(0).getName());
        // 第二次命中本地缓存
        verify(channelDOMapper, times(1)).selectAllEnabled();
        verify(valueOperations, times(1)).get(RedisKeyConstants.activeChannelSnapshotKey());
    }

    @Test
    void shouldFallBackToMySqlWhenChannelRedisIsUnavailable() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisKeyConstants.activeChannelSnapshotKey()))
                .thenThrow(new IllegalStateException("redis unavailable"));
        when(channelDOMapper.selectAllEnabled()).thenReturn(List.of(
                ChannelDO.builder().id(1L).name("Java").build()));

        var response = service.findChannelList();

        assertEquals(1, response.getData().size());
        assertEquals("Java", response.getData().get(0).getName());
        verify(channelDOMapper).selectAllEnabled();
    }

    @Test
    void shouldUseDoubleCheckAfterAcquiringDiscoverPageRebuildLock() throws InterruptedException {
        String pageKey = "feed:discover:cursor:v1:channel:0:cursor:first";
        String lockKey = "lock:feed:discover:cursor:v1:channel:0:cursor:first";
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisKeyConstants.buildDiscoverFeedVersionKey(null))).thenReturn("v1");
        when(valueOperations.get(pageKey)).thenReturn(null, "{\"notes\":[],\"nextCursor\":null}");
        when(redissonClient.getLock(lockKey)).thenReturn(rebuildLock);
        when(rebuildLock.tryLock(0, 5L, TimeUnit.SECONDS)).thenReturn(true);
        FindDiscoverNoteListReqVO request = new FindDiscoverNoteListReqVO();
        request.setCursor(0L);

        var response = service.findDiscoverNoteList(request);

        assertEquals(0, response.getData().size());
        verify(noteDOMapper, never()).selectDiscoverPageListByCursor(any(), any(), anyLong());
    }

    @Test
    void shouldNotRetryMySqlWhenDiscoverPageRebuildFails() throws InterruptedException {
        String pageKey = "feed:discover:cursor:v1:channel:0:cursor:first";
        String lockKey = "lock:feed:discover:cursor:v1:channel:0:cursor:first";
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisKeyConstants.buildDiscoverFeedVersionKey(null))).thenReturn("v1");
        when(valueOperations.get(pageKey)).thenReturn(null);
        when(redissonClient.getLock(lockKey)).thenReturn(rebuildLock);
        when(rebuildLock.tryLock(0, 5L, TimeUnit.SECONDS)).thenReturn(true);
        when(noteDOMapper.selectDiscoverPageListByCursor(null, null, 11L))
                .thenThrow(new IllegalStateException("mysql unavailable"));
        FindDiscoverNoteListReqVO request = new FindDiscoverNoteListReqVO();
        request.setCursor(0L);

        assertThrows(IllegalStateException.class, () -> service.findDiscoverNoteList(request));

        verify(noteDOMapper, times(1)).selectDiscoverPageListByCursor(null, null, 11L);
    }
}
