package hk.ljx.fishhub.note.biz.service.impl;

import hk.ljx.fishhub.count.dto.FindNoteCountsByIdRspDTO;
import hk.ljx.fishhub.note.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.note.biz.domain.dataobject.ChannelDO;
import hk.ljx.fishhub.note.biz.domain.dataobject.NoteDO;
import hk.ljx.fishhub.note.biz.domain.dataobject.TopicDO;
import hk.ljx.fishhub.note.biz.domain.mapper.ChannelDOMapper;
import hk.ljx.fishhub.note.biz.domain.mapper.NoteDOMapper;
import hk.ljx.fishhub.note.biz.domain.mapper.TopicDOMapper;
import hk.ljx.fishhub.note.biz.model.vo.FindDiscoverNoteListReqVO;
import hk.ljx.fishhub.note.biz.model.vo.FindTopicListReqVO;
import hk.ljx.fishhub.count.client.CountClient;
import hk.ljx.fishhub.user.client.UserClient;
import hk.ljx.fishhub.user.dto.rsp.FindUserByIdRspDTO;
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
    private UserClient userClient;
    @Mock
    private CountClient countClient;
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

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "safeRedisUtil", new hk.ljx.framework.common.util.SafeRedisUtil(stringRedisTemplate));
    }

    @Test
    void shouldRequestCountsOnceWhenCursorPageCacheMisses() {
        String pageKey = "feed:discover:channel:0:cursor:first";
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(pageKey)).thenReturn(null);
        when(noteDOMapper.selectDiscoverPageListByCursor(null, null, 21L)).thenReturn(List.of(
                NoteDO.builder().id(101L).creatorId(10L).title("标题").build()));
        when(userClient.findByIds(List.of(10L))).thenReturn(List.of(
                FindUserByIdRspDTO.builder().id(10L).nickName("作者").build()));
        when(countClient.findByNoteIds(List.of(101L))).thenReturn(List.of(
                FindNoteCountsByIdRspDTO.builder().noteId(101L).likeTotal(3L).build()));
        FindDiscoverNoteListReqVO request = new FindDiscoverNoteListReqVO();
        request.setCursor(0L);

        var response = service.findDiscoverNoteList(request);

        assertEquals(1, response.getData().size());
        assertEquals("3", response.getData().get(0).getLikeTotal());
        verify(countClient, times(1)).findByNoteIds(List.of(101L));
    }

    @Test
    void shouldDeleteCorruptedCursorPageCacheAndReloadIt() {
        String pageKey = "feed:discover:channel:0:cursor:first";
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(pageKey)).thenReturn("{");
        when(noteDOMapper.selectDiscoverPageListByCursor(null, null, 21L)).thenReturn(List.of(
                NoteDO.builder().id(101L).creatorId(10L).title("标题").build()));
        when(userClient.findByIds(List.of(10L))).thenReturn(List.of(
                FindUserByIdRspDTO.builder().id(10L).nickName("作者").build()));
        when(countClient.findByNoteIds(List.of(101L))).thenReturn(List.of(
                FindNoteCountsByIdRspDTO.builder().noteId(101L).likeTotal(3L).build()));
        FindDiscoverNoteListReqVO request = new FindDiscoverNoteListReqVO();
        request.setCursor(0L);

        service.findDiscoverNoteList(request);

        verify(stringRedisTemplate, atLeastOnce()).delete(pageKey);
    }

    @Test
    void shouldFallBackToMySqlWhenDiscoverRedisIsUnavailable() {
        String pageKey = "feed:discover:channel:0:cursor:first";
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(pageKey)).thenThrow(new IllegalStateException("redis unavailable"));
        when(noteDOMapper.selectDiscoverPageListByCursor(null, null, 21L)).thenReturn(List.of(
                NoteDO.builder().id(101L).creatorId(10L).title("标题").build()));
        when(userClient.findByIds(List.of(10L))).thenReturn(List.of(
                FindUserByIdRspDTO.builder().id(10L).nickName("作者").build()));
        when(countClient.findByNoteIds(List.of(101L))).thenReturn(List.of(
                FindNoteCountsByIdRspDTO.builder().noteId(101L).likeTotal(3L).build()));
        FindDiscoverNoteListReqVO request = new FindDiscoverNoteListReqVO();
        request.setCursor(0L);

        var response = service.findDiscoverNoteList(request);

        assertEquals(1, response.getData().size());
        verify(noteDOMapper).selectDiscoverPageListByCursor(null, null, 21L);
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
    void shouldUsePerChannelFeedKey() {
        String channelPageKey = "feed:discover:channel:1:cursor:first";
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(channelPageKey))
                .thenReturn("{\"notes\":[],\"nextCursor\":null}");
        FindDiscoverNoteListReqVO request = new FindDiscoverNoteListReqVO();
        request.setCursor(0L);
        request.setChannelId(1L);

        var response = service.findDiscoverNoteList(request);

        assertEquals(0, response.getData().size());
        verify(valueOperations).get(channelPageKey);
    }

    @Test
    void shouldUseBakedCountsWithoutFeignWhenDiscoverSnapshotIsHit() {
        String pageKey = "feed:discover:channel:0:cursor:first";
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(pageKey))
                .thenReturn("{\"notes\":[{\"noteId\":101,\"type\":0,\"title\":\"标题\",\"likeTotal\":\"5\",\"isLiked\":false}],\"nextCursor\":null}");
        FindDiscoverNoteListReqVO request = new FindDiscoverNoteListReqVO();
        request.setCursor(0L);

        var response = service.findDiscoverNoteList(request);

        assertEquals(1, response.getData().size());
        assertEquals("5", response.getData().get(0).getLikeTotal());
        // 快照命中时直接使用烘焙的点赞数，零 Feign RPC 调用
        verify(countClient, never()).findByNoteIds(any());
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
        String pageKey = "feed:discover:channel:0:cursor:first";
        String lockKey = "lock:feed:discover:channel:0:cursor:first";
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(pageKey)).thenReturn(null, "{\"notes\":[],\"nextCursor\":null}");
        when(redissonClient.getLock(lockKey)).thenReturn(rebuildLock);
        when(rebuildLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
        FindDiscoverNoteListReqVO request = new FindDiscoverNoteListReqVO();
        request.setCursor(0L);

        var response = service.findDiscoverNoteList(request);

        assertEquals(0, response.getData().size());
        verify(noteDOMapper, never()).selectDiscoverPageListByCursor(any(), any(), anyLong());
    }

    @Test
    void shouldNotRetryMySqlWhenDiscoverPageRebuildFails() throws InterruptedException {
        String pageKey = "feed:discover:channel:0:cursor:first";
        String lockKey = "lock:feed:discover:channel:0:cursor:first";
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(pageKey)).thenReturn(null);
        when(redissonClient.getLock(lockKey)).thenReturn(rebuildLock);
        when(rebuildLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(noteDOMapper.selectDiscoverPageListByCursor(null, null, 21L))
                .thenThrow(new IllegalStateException("mysql unavailable"));
        FindDiscoverNoteListReqVO request = new FindDiscoverNoteListReqVO();
        request.setCursor(0L);

        assertThrows(IllegalStateException.class, () -> service.findDiscoverNoteList(request));

        verify(noteDOMapper, times(1)).selectDiscoverPageListByCursor(null, null, 21L);
    }
}
