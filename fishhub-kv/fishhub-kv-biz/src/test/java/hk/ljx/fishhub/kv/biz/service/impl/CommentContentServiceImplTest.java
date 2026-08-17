package hk.ljx.fishhub.kv.biz.service.impl;

import hk.ljx.fishhub.kv.biz.domain.dataobject.CommentContentDO;
import hk.ljx.fishhub.kv.biz.domain.dataobject.CommentContentPrimaryKey;
import hk.ljx.fishhub.kv.biz.domain.repository.CommentContentRepository;
import hk.ljx.fishhub.kv.dto.req.BatchFindCommentContentReqDTO;
import hk.ljx.fishhub.kv.dto.req.FindCommentContentReqDTO;
import hk.ljx.fishhub.kv.dto.rsp.FindCommentContentRspDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.cassandra.core.CassandraTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentContentServiceImplTest {

    private static final String M1 = "2026-07";
    private static final String M2 = "2026-08";

    private static final String U1 = "11111111-1111-1111-1111-111111111111";
    private static final String U2 = "22222222-2222-2222-2222-222222222222";
    private static final String U3 = "33333333-3333-3333-3333-333333333333";

    @Mock
    private CassandraTemplate cassandraTemplate;
    @Mock
    private CommentContentRepository commentContentRepository;
    @InjectMocks
    private CommentContentServiceImpl service;

    private BatchFindCommentContentReqDTO request(Long noteId, List<FindCommentContentReqDTO> keys) {
        return BatchFindCommentContentReqDTO.builder()
                .noteId(noteId)
                .commentContentKeys(keys)
                .build();
    }

    private FindCommentContentReqDTO key(String yearMonth, String contentId) {
        FindCommentContentReqDTO dto = new FindCommentContentReqDTO();
        dto.setYearMonth(yearMonth);
        dto.setContentId(contentId);
        return dto;
    }

    private CommentContentDO doc(String yearMonth, String contentId, String content) {
        return CommentContentDO.builder()
                .primaryKey(CommentContentPrimaryKey.builder()
                        .noteId(1L)
                        .yearMonth(yearMonth)
                        .contentId(UUID.fromString(contentId))
                        .build())
                .content(content)
                .build();
    }

    @Test
    void shouldReadAllKeysInOneBatchCallForSameMonth() {
        List<FindCommentContentReqDTO> keys = List.of(key(M2, U1), key(M2, U2), key(M2, U3));
        when(commentContentRepository
                .findByPrimaryKeyNoteIdAndPrimaryKeyYearMonthInAndPrimaryKeyContentIdIn(
                        1L, List.of(M2), List.of(UUID.fromString(U1), UUID.fromString(U2), UUID.fromString(U3))))
                .thenReturn(List.of(doc(M2, U3, "内容3"), doc(M2, U1, "内容1")));

        List<FindCommentContentRspDTO> data = (List<FindCommentContentRspDTO>) service
                .batchFindCommentContent(request(1L, keys)).getData();

        // 只发 1 次批量查询，且结果按入参顺序重组、缺失的 key 跳过
        verify(commentContentRepository, times(1))
                .findByPrimaryKeyNoteIdAndPrimaryKeyYearMonthInAndPrimaryKeyContentIdIn(
                        eq(1L), eq(List.of(M2)),
                        eq(List.of(UUID.fromString(U1), UUID.fromString(U2), UUID.fromString(U3))));
        assertEquals(2, data.size());
        assertEquals(U1, data.get(0).getContentId());
        assertEquals("内容1", data.get(0).getContent());
        assertEquals(U3, data.get(1).getContentId());
        assertEquals("内容3", data.get(1).getContent());
    }

    @Test
    void shouldSplitBatchCallsByYearMonth() {
        List<FindCommentContentReqDTO> keys = List.of(key(M1, U1), key(M2, U2));
        when(commentContentRepository
                .findByPrimaryKeyNoteIdAndPrimaryKeyYearMonthInAndPrimaryKeyContentIdIn(
                        1L, List.of(M1), List.of(UUID.fromString(U1))))
                .thenReturn(List.of(doc(M1, U1, "上月内容")));
        when(commentContentRepository
                .findByPrimaryKeyNoteIdAndPrimaryKeyYearMonthInAndPrimaryKeyContentIdIn(
                        1L, List.of(M2), List.of(UUID.fromString(U2))))
                .thenReturn(List.of(doc(M2, U2, "本月内容")));

        List<FindCommentContentRspDTO> data = (List<FindCommentContentRspDTO>) service
                .batchFindCommentContent(request(1L, keys)).getData();

        verify(commentContentRepository, times(1))
                .findByPrimaryKeyNoteIdAndPrimaryKeyYearMonthInAndPrimaryKeyContentIdIn(eq(1L), eq(List.of(M1)), eq(List.of(UUID.fromString(U1))));
        verify(commentContentRepository, times(1))
                .findByPrimaryKeyNoteIdAndPrimaryKeyYearMonthInAndPrimaryKeyContentIdIn(eq(1L), eq(List.of(M2)), eq(List.of(UUID.fromString(U2))));
        assertEquals(2, data.size());
        assertEquals(U1, data.get(0).getContentId());
        assertEquals(U2, data.get(1).getContentId());
    }

    @Test
    void shouldMatchKeysCaseInsensitively() {
        String upperU1 = U1.toUpperCase(java.util.Locale.ROOT);
        List<FindCommentContentReqDTO> keys = List.of(key(M2, upperU1));
        when(commentContentRepository
                .findByPrimaryKeyNoteIdAndPrimaryKeyYearMonthInAndPrimaryKeyContentIdIn(
                        1L, List.of(M2), List.of(UUID.fromString(upperU1))))
                .thenReturn(List.of(doc(M2, U1, "内容1")));

        List<FindCommentContentRspDTO> data = (List<FindCommentContentRspDTO>) service
                .batchFindCommentContent(request(1L, keys)).getData();

        // 大写入参同样命中（map 键已归一化），响应按入参原样返回
        assertEquals(1, data.size());
        assertEquals(upperU1, data.get(0).getContentId());
        assertEquals("内容1", data.get(0).getContent());
    }

    @Test
    void shouldSkipMissingKeysAndKeepInputOrder() {
        List<FindCommentContentReqDTO> keys = List.of(key(M2, U1), key(M2, U2), key(M2, U3));
        // 只命中 U2，U1/U3 缺失
        when(commentContentRepository
                .findByPrimaryKeyNoteIdAndPrimaryKeyYearMonthInAndPrimaryKeyContentIdIn(
                        1L, List.of(M2), List.of(UUID.fromString(U1), UUID.fromString(U2), UUID.fromString(U3))))
                .thenReturn(List.of(doc(M2, U2, "仅U2存在")));

        List<FindCommentContentRspDTO> data = (List<FindCommentContentRspDTO>) service
                .batchFindCommentContent(request(1L, keys)).getData();

        assertEquals(1, data.size());
        assertEquals(U2, data.get(0).getContentId());
        assertEquals("仅U2存在", data.get(0).getContent());
    }
}
