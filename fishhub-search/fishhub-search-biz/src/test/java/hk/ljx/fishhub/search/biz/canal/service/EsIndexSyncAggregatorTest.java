package hk.ljx.fishhub.search.biz.canal.service;

import hk.ljx.fishhub.search.biz.domain.mapper.SelectMapper;
import hk.ljx.fishhub.search.biz.index.NoteIndex;
import hk.ljx.fishhub.search.biz.index.UserIndex;
import org.apache.lucene.search.TotalHits;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EsIndexSyncAggregatorTest {

    private RestHighLevelClient client;
    private SelectMapper selectMapper;
    private EsIndexSyncAggregator aggregator;

    @BeforeEach
    void setUp() throws Exception {
        client = mock(RestHighLevelClient.class);
        selectMapper = mock(SelectMapper.class);
        aggregator = new EsIndexSyncAggregator(client, selectMapper);
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
    void shouldRequeuePendingNotesWhenBulkSyncFails() throws Exception {
        when(selectMapper.selectEsNoteIndexDataByIds(anyList())).thenReturn(List.of(row("1")));
        when(client.bulk(any(BulkRequest.class), any(RequestOptions.class)))
                .thenThrow(new RuntimeException("ES timeout"))
                .thenReturn(mock(BulkResponse.class));

        aggregator.submitNote(1L);
        // 第一次 flush 失败，应重新入队
        aggregator.flush();

        // 第二次 flush 应重试该 ID
        aggregator.flush();
        verify(client, times(2)).bulk(any(BulkRequest.class), any(RequestOptions.class));
    }

    @Test
    void shouldAbandonNotesAfterMaxRetries() throws Exception {
        when(selectMapper.selectEsNoteIndexDataByIds(anyList())).thenReturn(List.of(row("1")));
        when(client.bulk(any(BulkRequest.class), any(RequestOptions.class)))
                .thenThrow(new RuntimeException("ES timeout"));

        aggregator.submitNote(1L);
        // 第 1 次执行与失败 (retries 变为 1, re-queued)
        aggregator.flush();
        // 第 2 次执行与失败 (retries 变为 2, re-queued)
        aggregator.flush();
        // 第 3 次执行与失败 (retries 变为 3, re-queued)
        aggregator.flush();
        // 第 4 次执行与失败 (retries 变为 4 > 3, 丢弃不再 re-queue)
        aggregator.flush();

        // 第 5 次 flush：集合已空，不再触发 bulk
        aggregator.flush();
        verify(client, times(4)).bulk(any(BulkRequest.class), any(RequestOptions.class));
    }

    private static Map<String, Object> row(String id) {
        return Map.of("id", id, "title", "t", "creator_id", "10");
    }
}