package hk.ljx.fishhub.search.biz.canal.service;

import hk.ljx.framework.common.enums.StatusEnum;
import hk.ljx.fishhub.search.biz.canal.model.CanalFlatMessageDTO;
import hk.ljx.fishhub.search.biz.domain.mapper.SelectMapper;
import hk.ljx.fishhub.search.biz.enums.NoteStatusEnum;
import hk.ljx.fishhub.search.biz.enums.NoteVisibleEnum;
import hk.ljx.fishhub.search.biz.index.NoteIndex;
import hk.ljx.fishhub.search.biz.index.UserIndex;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.rest.RestStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class CanalSyncService {

    private final RestHighLevelClient restHighLevelClient;
    private final SelectMapper selectMapper;
    private final EsIndexSyncAggregator esIndexSyncAggregator;

    /**
     * 处理 Canal FlatMessage 消息
     */
    public void processFlatMessage(CanalFlatMessageDTO message) {
        if (message == null || Boolean.TRUE.equals(message.getIsDdl()) || message.getData() == null) {
            return;
        }

        String table = message.getTable();
        String eventType = message.getType();
        List<Map<String, Object>> rows = message.getData();

        log.info("==> CanalSyncService 接收 Binlog 事件: database={}, table={}, eventType={}, rows={}",
                message.getDatabase(), table, eventType, rows.size());

        for (Map<String, Object> columnMap : rows) {
            try {
                processRowEvent(columnMap, table, eventType);
            } catch (Exception e) {
                log.error("==> 同步 ES 索引失败, table={}, eventType={}, columnMap={}", table, eventType, columnMap, e);
                throw new RuntimeException("同步 ES 索引异常", e);
            }
        }
    }

    private void processRowEvent(Map<String, Object> columnMap, String table, String eventType) throws Exception {
        switch (table) {
            case "t_note" -> handleNoteEvent(columnMap, eventType);
            case "t_user" -> handleUserEvent(columnMap, eventType);
            case "t_note_count" -> handleNoteCountEvent(columnMap, eventType);
            case "t_user_count" -> handleUserCountEvent(columnMap, eventType);
            default -> log.debug("Table: {} 忽略同步", table);
        }
    }

    private void handleNoteCountEvent(Map<String, Object> columnMap, String eventType) throws Exception {
        if (!"INSERT".equalsIgnoreCase(eventType)
                && !"UPDATE".equalsIgnoreCase(eventType)
                && !"DELETE".equalsIgnoreCase(eventType)) {
            return;
        }
        Long noteId = parseRequiredId(columnMap, "note_id");
        if (noteId != null) {
            // 计数变化只影响文档内的数值字段，交由聚合器合并去重 + Bulk 重建
            esIndexSyncAggregator.submitNote(noteId);
        }
    }

    private void handleUserCountEvent(Map<String, Object> columnMap, String eventType) throws Exception {
        if (!"INSERT".equalsIgnoreCase(eventType)
                && !"UPDATE".equalsIgnoreCase(eventType)
                && !"DELETE".equalsIgnoreCase(eventType)) {
            return;
        }
        Long userId = parseRequiredId(columnMap, "user_id");
        if (userId != null) {
            esIndexSyncAggregator.submitUser(userId);
        }
    }

    private Long parseRequiredId(Map<String, Object> columnMap, String columnName) {
        Object value = columnMap.get(columnName);
        return value == null ? null : Long.parseLong(value.toString());
    }

    private void handleNoteEvent(Map<String, Object> columnMap, String eventType) throws Exception {
        Object idObj = columnMap.get("id");
        if (idObj == null) return;
        Long noteId = Long.parseLong(idObj.toString());

        if ("INSERT".equalsIgnoreCase(eventType)) {
            syncNoteIndex(noteId);
        } else if ("UPDATE".equalsIgnoreCase(eventType)) {
            Object statusObj = columnMap.get("status");
            Integer status = statusObj == null ? null : Integer.parseInt(statusObj.toString());
            Object visibleObj = columnMap.get("visible");
            Integer visible = visibleObj == null ? null : Integer.parseInt(visibleObj.toString());

            if (Objects.equals(visible, NoteVisibleEnum.PRIVATE.getCode())
                    || Objects.equals(status, NoteStatusEnum.DELETED.getCode())
                    || Objects.equals(status, NoteStatusEnum.DOWNED.getCode())) {
                deleteNoteDocument(String.valueOf(noteId));
                return;
            }
            syncNoteIndex(noteId);
        } else if ("DELETE".equalsIgnoreCase(eventType)) {
            deleteNoteDocument(String.valueOf(noteId));
        }
    }

    private void handleUserEvent(Map<String, Object> columnMap, String eventType) throws Exception {
        Object idObj = columnMap.get("id");
        if (idObj == null) return;
        Long userId = Long.parseLong(idObj.toString());

        if ("INSERT".equalsIgnoreCase(eventType)) {
            syncUserIndex(userId);
        } else if ("UPDATE".equalsIgnoreCase(eventType)) {
            Object statusObj = columnMap.get("status");
            Integer status = statusObj == null ? null : Integer.parseInt(statusObj.toString());
            Object isDeletedObj = columnMap.get("is_deleted");
            Integer isDeleted = isDeletedObj == null ? null : Integer.parseInt(isDeletedObj.toString());

            if (Objects.equals(status, StatusEnum.DISABLED.getValue())
                    || Objects.equals(isDeleted, 1)) {
                deleteUserDocument(String.valueOf(userId));
                return;
            }
            syncNotesIndexAndUserIndex(userId);
        } else if ("DELETE".equalsIgnoreCase(eventType)) {
            deleteUserDocument(String.valueOf(userId));
        }
    }

    public void syncUserIndex(Long userId) throws Exception {
        List<Map<String, Object>> userResult = selectMapper.selectEsUserIndexData(userId);
        if (userResult == null || userResult.isEmpty()) {
            deleteUserDocument(String.valueOf(userId));
            return;
        }

        for (Map<String, Object> recordMap : userResult) {
            IndexRequest indexRequest = new IndexRequest(UserIndex.NAME);
            indexRequest.id(String.valueOf(recordMap.get(UserIndex.FIELD_USER_ID)));
            indexRequest.source(recordMap);
            restHighLevelClient.index(indexRequest, RequestOptions.DEFAULT);
        }
    }

    public void syncNotesIndexAndUserIndex(Long userId) throws Exception {
        BulkRequest bulkRequest = new BulkRequest();

        // 1. 同步用户索引
        List<Map<String, Object>> userResult = selectMapper.selectEsUserIndexData(userId);
        if (userResult != null) {
            for (Map<String, Object> recordMap : userResult) {
                IndexRequest indexRequest = new IndexRequest(UserIndex.NAME);
                indexRequest.id(String.valueOf(recordMap.get(UserIndex.FIELD_USER_ID)));
                indexRequest.source(recordMap);
                bulkRequest.add(indexRequest);
            }
        }

        // 2. 同步笔记索引
        List<Map<String, Object>> noteResult = selectMapper.selectEsNoteIndexData(null, userId);
        if (noteResult != null) {
            for (Map<String, Object> recordMap : noteResult) {
                IndexRequest indexRequest = new IndexRequest(NoteIndex.NAME);
                indexRequest.id(String.valueOf(recordMap.get(NoteIndex.FIELD_NOTE_ID)));
                indexRequest.source(recordMap);
                bulkRequest.add(indexRequest);
            }
        }

        if (bulkRequest.numberOfActions() > 0) {
            BulkResponse bulkResponse = restHighLevelClient.bulk(bulkRequest, RequestOptions.DEFAULT);
            if (bulkResponse.hasFailures()) {
                throw new IllegalStateException("Elasticsearch 批量同步部分失败: " + bulkResponse.buildFailureMessage());
            }
        }
    }

    public void syncNoteIndex(Long noteId) throws Exception {
        List<Map<String, Object>> result = selectMapper.selectEsNoteIndexData(noteId, null);
        if (result == null || result.isEmpty()) {
            deleteNoteDocument(String.valueOf(noteId));
            return;
        }

        for (Map<String, Object> recordMap : result) {
            IndexRequest indexRequest = new IndexRequest(NoteIndex.NAME);
            indexRequest.id(String.valueOf(recordMap.get(NoteIndex.FIELD_NOTE_ID)));
            indexRequest.source(recordMap);
            restHighLevelClient.index(indexRequest, RequestOptions.DEFAULT);
        }
    }

    private void deleteNoteDocument(String documentId) throws Exception {
        DeleteRequest deleteRequest = new DeleteRequest(NoteIndex.NAME, documentId);
        try {
            restHighLevelClient.delete(deleteRequest, RequestOptions.DEFAULT);
        } catch (ElasticsearchStatusException e) {
            if (e.status() != RestStatus.NOT_FOUND) {
                throw e;
            }
        }
    }

    private void deleteUserDocument(String documentId) throws Exception {
        DeleteRequest deleteRequest = new DeleteRequest(UserIndex.NAME, documentId);
        try {
            restHighLevelClient.delete(deleteRequest, RequestOptions.DEFAULT);
        } catch (ElasticsearchStatusException e) {
            if (e.status() != RestStatus.NOT_FOUND) {
                throw e;
            }
        }
    }
}
