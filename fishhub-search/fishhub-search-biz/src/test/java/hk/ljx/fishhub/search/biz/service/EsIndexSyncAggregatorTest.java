package hk.ljx.fishhub.search.biz.service;

import hk.ljx.fishhub.search.biz.domain.mapper.SelectMapper;
import hk.ljx.fishhub.search.biz.index.NoteIndex;
import hk.ljx.fishhub.search.biz.index.UserIndex;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EsIndexSyncAggregatorTest {

    private static final String ES_RETRY_NOTE_KEY = "es:retry:note";

    private RestHighLevelClient client;
    private SelectMapper selectMapper;
    private StringRedisTemplate stringRedisTemplate;
    private ZSetOperations<String, String> zSetOps;
    private EsIndexSyncAggregator aggregator;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        client = mock(RestHighLevelClient.class);
        selectMapper = mock(SelectMapper.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        zSetOps = mock(ZSetOperations.class);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOps);
        aggregator = new EsIndexSyncAggregator(client, selectMapper, stringRedisTemplate);
        BulkResponse response = mock(BulkResponse.class);
        when(response.hasFailures()).thenReturn(false);
        when(client.bulk(any(BulkRequest.class), any(RequestOptions.class))).thenReturn(response);
    }

    @Test
    void shouldBulkIndexNoteOnFlush() throws Exception {
        when(selectMapper.selectEsNoteIndexDataByIds(anyList()))
                .thenReturn(List.of(row("1")));

        aggregator.submitNote(1L);
        aggregator.flush();

        ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client, times(1)).bulk(captor.capture(), any(RequestOptions.class));
        assertEquals(1, captor.getValue().numberOfActions());
        assertInstanceOf(IndexRequest.class, captor.getValue().requests().get(0));
        assertEquals("1", captor.getValue().requests().get(0).id());
    }

    @Test
    void shouldDeduplicatePendingNotes() throws Exception {
        when(selectMapper.selectEsNoteIndexDataByIds(anyList())).thenReturn(List.of(row("1"), row("2")));

        aggregator.submitNote(1L);
        aggregator.submitNote(2L);
        aggregator.submitNote(1L); // 重复提交应去重
        aggregator.flush();

        ArgumentCaptor<List<Long>> idCaptor = ArgumentCaptor.forClass(List.class);
        verify(selectMapper, times(1)).selectEsNoteIndexDataByIds(idCaptor.capture());
        assertEquals(List.of(1L, 2L), idCaptor.getValue().stream().sorted().toList());
        verify(client, times(1)).bulk(any(BulkRequest.class), any(RequestOptions.class));
    }

    @Test
    void shouldDeleteNoteDocumentWhenRowNoLongerIndexable() throws Exception {
        when(selectMapper.selectEsNoteIndexDataByIds(anyList())).thenReturn(List.of()); // 已删除/下架 → 查空

        aggregator.submitNote(9L);
        aggregator.flush();

        ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client, times(1)).bulk(captor.capture(), any(RequestOptions.class));
        assertEquals(1, captor.getValue().numberOfActions());
        assertInstanceOf(DeleteRequest.class, captor.getValue().requests().get(0));
    }

    @Test
    void shouldDeleteUserDocumentWhenUserNoLongerIndexable() throws Exception {
        when(selectMapper.selectEsUserIndexDataByIds(anyList())).thenReturn(List.of());

        aggregator.submitUser(5L);
        aggregator.flush();

        ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client, times(1)).bulk(captor.capture(), any(RequestOptions.class));
        assertInstanceOf(DeleteRequest.class, captor.getValue().requests().get(0));
        assertEquals(UserIndex.NAME, captor.getValue().requests().get(0).index());
    }

    @Test
    void shouldDoNothingWhenNoPending() throws Exception {
        aggregator.flush();
        verify(client, never()).bulk(any(BulkRequest.class), any(RequestOptions.class));
    }

    @Test
    void shouldEnqueueFailedIdsToRedisRetryZset() throws Exception {
        when(selectMapper.selectEsNoteIndexDataByIds(anyList())).thenReturn(List.of(row("1")));
        when(client.bulk(any(BulkRequest.class), any(RequestOptions.class)))
                .thenThrow(new RuntimeException("ES timeout"));

        aggregator.submitNote(1L);
        aggregator.flush();

        // 失败后写入 Redis 重试队列（无限重试，不再放回内存 pending）
        verify(zSetOps).add(eq(ES_RETRY_NOTE_KEY), eq("1"), anyDouble());
    }

    @Test
    void shouldRetryPendingNotesAndRemoveOnSuccess() throws Exception {
        when(selectMapper.selectEsNoteIndexDataByIds(anyList())).thenReturn(List.of(row("1")));
        when(zSetOps.rangeByScore(eq(ES_RETRY_NOTE_KEY), eq(0d), anyDouble()))
                .thenReturn(Set.of("1"));

        aggregator.retryPendingNotes();

        verify(client, times(1)).bulk(any(BulkRequest.class), any(RequestOptions.class));
        verify(zSetOps).remove(eq(ES_RETRY_NOTE_KEY), eq("1"));
    }

    @Test
    void shouldKeepRetryEntryOnFailureForInfiniteRetry() throws Exception {
        when(selectMapper.selectEsNoteIndexDataByIds(anyList())).thenReturn(List.of(row("1")));
        when(zSetOps.rangeByScore(eq(ES_RETRY_NOTE_KEY), eq(0d), anyDouble()))
                .thenReturn(Set.of("1"));
        when(client.bulk(any(BulkRequest.class), any(RequestOptions.class)))
                .thenThrow(new RuntimeException("ES timeout"));

        aggregator.retryPendingNotes();
        aggregator.retryPendingNotes();

        // 两次都重试且失败后不移除（无限重试）
        verify(client, times(2)).bulk(any(BulkRequest.class), any(RequestOptions.class));
        verify(zSetOps, never()).remove(any(String.class), any(Object[].class));
    }

    @Test
    void shouldSkipAndRemoveDirtyMembersWithoutBlockingRetry() throws Exception {
        when(selectMapper.selectEsNoteIndexDataByIds(anyList())).thenReturn(List.of(row("1")));
        when(zSetOps.rangeByScore(eq(ES_RETRY_NOTE_KEY), eq(0d), anyDouble()))
                .thenReturn(Set.of("1", "dirty"));

        aggregator.retryPendingNotes();

        // 脏 member 被清理移除，正常 id 继续重试，队列不被卡死
        verify(zSetOps, times(2)).remove(any(String.class), any(Object[].class));
        verify(client, times(1)).bulk(any(BulkRequest.class), any(RequestOptions.class));
    }

    @Test
    void shouldNotCallBulkWhenOnlyDirtyMembersExist() throws Exception {
        when(zSetOps.rangeByScore(eq(ES_RETRY_NOTE_KEY), eq(0d), anyDouble()))
                .thenReturn(Set.of("dirty", "abc"));

        aggregator.retryPendingNotes();

        // 全部为脏数据：只清理，不触发 ES bulk
        verify(zSetOps).remove(any(String.class), any(Object[].class));
        verify(client, never()).bulk(any(BulkRequest.class), any(RequestOptions.class));
    }

    private static Map<String, Object> row(String id) {
        return Map.of("id", id, "title", "t", "creator_id", "10");
    }
}
