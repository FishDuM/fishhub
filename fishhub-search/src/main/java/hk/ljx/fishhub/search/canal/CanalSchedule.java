package hk.ljx.fishhub.search.canal;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import hk.ljx.fishhub.search.domain.mapper.SelectMapper;
import hk.ljx.fishhub.search.enums.NoteStatusEnum;
import hk.ljx.fishhub.search.enums.NoteVisibleEnum;
import hk.ljx.fishhub.search.index.NoteIndex;
import hk.ljx.fishhub.search.index.UserIndex;
import hk.ljx.framework.common.enums.StatusEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** 将 Canal 中的用户、笔记变更同步到 Elasticsearch。 */
@Component
@Slf4j
public class CanalSchedule implements Runnable {

    @Resource
    private CanalProperties canalProperties;
    @Resource
    private CanalConnector canalConnector;
    @Resource
    private RestHighLevelClient restHighLevelClient;
    @Resource
    private SelectMapper selectMapper;

    @Override
    @Scheduled(fixedDelay = 100)
    public void run() {
        long batchId = -1;
        try {
            Message message = canalConnector.getWithoutAck(canalProperties.getBatchSize());
            batchId = message.getId();
            if (batchId == -1 || message.getEntries().isEmpty()) {
                TimeUnit.SECONDS.sleep(1);
                return;
            }

            processEntries(message.getEntries());
            canalConnector.ack(batchId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("消费 Canal 批次数据异常, batchId={}", batchId, e);
            if (batchId != -1) {
                canalConnector.rollback(batchId);
            }
        }
    }

    private void processEntries(List<CanalEntry.Entry> entries) throws Exception {
        for (CanalEntry.Entry entry : entries) {
            if (entry.getEntryType() != CanalEntry.EntryType.ROWDATA) {
                continue;
            }

            CanalEntry.EventType eventType = entry.getHeader().getEventType();
            String table = entry.getHeader().getTableName();
            CanalEntry.RowChange rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
            for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
                List<CanalEntry.Column> columns = eventType == CanalEntry.EventType.DELETE
                        ? rowData.getBeforeColumnsList() : rowData.getAfterColumnsList();
                processEvent(toColumnMap(columns), table, eventType);
            }
        }
    }

    private void processEvent(Map<String, Object> columns, String table, CanalEntry.EventType eventType) throws Exception {
        switch (table) {
            case "t_note" -> handleNoteEvent(columns, eventType);
            case "t_user" -> handleUserEvent(columns, eventType);
            default -> log.debug("忽略未支持的 Canal 表: {}", table);
        }
    }

    private void handleNoteEvent(Map<String, Object> columns, CanalEntry.EventType eventType) throws Exception {
        Long noteId = readLong(columns, "id");
        if (noteId == null) {
            log.warn("忽略无 ID 的笔记变更: {}", columns);
            return;
        }

        if (eventType == CanalEntry.EventType.DELETE) {
            deleteDocument(NoteIndex.NAME, noteId);
            return;
        }

        Integer status = readInteger(columns, "status");
        Integer visible = readInteger(columns, "visible");
        if (Objects.equals(status, NoteStatusEnum.NORMAL.getCode())
                && Objects.equals(visible, NoteVisibleEnum.PUBLIC.getCode())) {
            syncNoteIndex(noteId);
        } else {
            deleteDocument(NoteIndex.NAME, noteId);
        }
    }

    private void handleUserEvent(Map<String, Object> columns, CanalEntry.EventType eventType) throws Exception {
        Long userId = readLong(columns, "id");
        if (userId == null) {
            log.warn("忽略无 ID 的用户变更: {}", columns);
            return;
        }

        if (eventType == CanalEntry.EventType.DELETE) {
            deleteDocument(UserIndex.NAME, userId);
            return;
        }

        Integer status = readInteger(columns, "status");
        Integer isDeleted = readInteger(columns, "is_deleted");
        if (Objects.equals(status, StatusEnum.ENABLE.getValue()) && Objects.equals(isDeleted, 0)) {
            syncUserAndNotesIndex(userId);
        } else {
            deleteDocument(UserIndex.NAME, userId);
        }
    }

    private void syncNoteIndex(Long noteId) throws Exception {
        indexDocuments(NoteIndex.NAME, NoteIndex.FIELD_NOTE_ID,
                selectMapper.selectEsNoteIndexData(noteId, null));
    }

    private void syncUserAndNotesIndex(Long userId) throws Exception {
        BulkRequest request = new BulkRequest();
        addIndexRequests(request, UserIndex.NAME, UserIndex.FIELD_USER_ID,
                selectMapper.selectEsUserIndexData(userId));
        addIndexRequests(request, NoteIndex.NAME, NoteIndex.FIELD_NOTE_ID,
                selectMapper.selectEsNoteIndexData(null, userId));
        if (request.numberOfActions() > 0) {
            restHighLevelClient.bulk(request, RequestOptions.DEFAULT);
        }
    }

    private void indexDocuments(String indexName, String idField, List<Map<String, Object>> documents) throws Exception {
        for (Map<String, Object> document : documents) {
            restHighLevelClient.index(new IndexRequest(indexName)
                    .id(String.valueOf(document.get(idField)))
                    .source(document), RequestOptions.DEFAULT);
        }
    }

    private void addIndexRequests(BulkRequest request, String indexName, String idField,
                                  List<Map<String, Object>> documents) {
        for (Map<String, Object> document : documents) {
            request.add(new IndexRequest(indexName)
                    .id(String.valueOf(document.get(idField)))
                    .source(document));
        }
    }

    private void deleteDocument(String indexName, Long documentId) throws Exception {
        restHighLevelClient.delete(new DeleteRequest(indexName, String.valueOf(documentId)), RequestOptions.DEFAULT);
    }

    private Map<String, Object> toColumnMap(List<CanalEntry.Column> columns) {
        Map<String, Object> values = new HashMap<>(columns.size());
        for (CanalEntry.Column column : columns) {
            if (column != null) {
                values.put(column.getName(), column.getValue());
            }
        }
        return values;
    }

    private Long readLong(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? null : Long.valueOf(value.toString());
    }

    private Integer readInteger(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? null : Integer.valueOf(value.toString());
    }
}
