package hk.ljx.fishhub.search.biz.canal.service;

import hk.ljx.fishhub.search.biz.domain.mapper.SelectMapper;
import hk.ljx.fishhub.search.biz.index.NoteIndex;
import hk.ljx.fishhub.search.biz.index.UserIndex;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.VersionType;
import org.elasticsearch.rest.RestStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ES 索引同步聚合器：合并短窗口内的多次计数变更，批量提交至 Elasticsearch。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EsIndexSyncAggregator {

    private static final long WINDOW_MS = 5000;
    /** 积压达到该量即立即冲刷，防止内存积压过大 */
    private static final int FLUSH_CAP = 500;
    /** 单次 Bulk 提交的文档条数上限 */
    private static final int BULK_BATCH_SIZE = 500;

    private static final int MAX_RETRY_TIMES = 3;

    private final RestHighLevelClient restHighLevelClient;
    private final SelectMapper selectMapper;

    private final Set<Long> pendingNoteIds = new HashSet<>();
    private final Set<Long> pendingUserIds = new HashSet<>();
    private final Map<Long, Integer> noteRetryCounts = new HashMap<>();
    private final Map<Long, Integer> userRetryCounts = new HashMap<>();
    private final Object noteFlushLock = new Object();
    private final Object userFlushLock = new Object();

    /**
     * 笔记计数变化后提交同步（去重合并）
     */
    public void submitNote(Long noteId) {
        if (noteId == null) {
            return;
        }
        boolean fire;
        synchronized (pendingNoteIds) {
            pendingNoteIds.add(noteId);
            fire = pendingNoteIds.size() >= FLUSH_CAP;
        }
        if (fire) {
            flushNotes();
        }
    }

    /**
     * 用户计数变化后提交同步（去重合并）
     */
    public void submitUser(Long userId) {
        if (userId == null) {
            return;
        }
        boolean fire;
        synchronized (pendingUserIds) {
            pendingUserIds.add(userId);
            fire = pendingUserIds.size() >= FLUSH_CAP;
        }
        if (fire) {
            flushUsers();
        }
    }

    /**
     * 定时冲刷：高频合并 + 批量提交
     */
    @Scheduled(fixedDelay = WINDOW_MS)
    public void flush() {
        flushNotes();
        flushUsers();
    }

    public void flushNotes() {
        synchronized (noteFlushLock) {
            List<Long> ids = drain(pendingNoteIds);
            if (ids.isEmpty()) {
                return;
            }
            try {
                bulkSyncNotes(ids);
                ids.forEach(noteRetryCounts::remove);
            } catch (Exception e) {
                log.error("ES 笔记索引批量同步失败, idsSize={}", ids.size(), e);
                List<Long> retryIds = new ArrayList<>();
                for (Long id : ids) {
                    int retries = noteRetryCounts.getOrDefault(id, 0) + 1;
                    if (retries <= MAX_RETRY_TIMES) {
                        noteRetryCounts.put(id, retries);
                        retryIds.add(id);
                    } else {
                        noteRetryCounts.remove(id);
                        log.error("ES 笔记索引同步重试已达上限({}次)，放弃重试, 依靠对账兜底: noteId={}", MAX_RETRY_TIMES, id);
                    }
                }
                if (!retryIds.isEmpty()) {
                    synchronized (pendingNoteIds) {
                        pendingNoteIds.addAll(retryIds);
                    }
                }
            }
        }
    }

    public void flushUsers() {
        synchronized (userFlushLock) {
            List<Long> ids = drain(pendingUserIds);
            if (ids.isEmpty()) {
                return;
            }
            try {
                bulkSyncUsers(ids);
                ids.forEach(userRetryCounts::remove);
            } catch (Exception e) {
                log.error("ES 用户索引批量同步失败, idsSize={}", ids.size(), e);
                List<Long> retryIds = new ArrayList<>();
                for (Long id : ids) {
                    int retries = userRetryCounts.getOrDefault(id, 0) + 1;
                    if (retries <= MAX_RETRY_TIMES) {
                        userRetryCounts.put(id, retries);
                        retryIds.add(id);
                    } else {
                        userRetryCounts.remove(id);
                        log.error("ES 用户索引同步重试已达上限({}次)，放弃重试, 依靠对账兜底: userId={}", MAX_RETRY_TIMES, id);
                    }
                }
                if (!retryIds.isEmpty()) {
                    synchronized (pendingUserIds) {
                        pendingUserIds.addAll(retryIds);
                    }
                }
            }
        }
    }

    private static List<Long> drain(Set<Long> pending) {
        List<Long> batch;
        synchronized (pending) {
            if (pending.isEmpty()) {
                return List.of();
            }
            batch = new ArrayList<>(pending);
            pending.clear();
        }
        return batch;
    }

    private void bulkSyncNotes(List<Long> noteIds) throws Exception {
        Map<Long, Map<String, Object>> rowById = queryRows(selectMapper.selectEsNoteIndexDataByIds(noteIds));
        BulkRequest bulk = new BulkRequest();
        for (Long noteId : noteIds) {
            Map<String, Object> row = rowById.get(noteId);
            if (row == null) {
                // 笔记已不可检索（删除/下架/私密），从索引移除
                bulk.add(new DeleteRequest(NoteIndex.NAME, String.valueOf(noteId)));
            } else {
                IndexRequest request = new IndexRequest(NoteIndex.NAME).id(String.valueOf(noteId)).source(row);
                Object versionObj = row.get("update_time_millis");
                if (versionObj != null) {
                    long version = Long.parseLong(String.valueOf(versionObj));
                    if (version > 0) {
                        request.version(version).versionType(VersionType.EXTERNAL);
                    }
                }
                bulk.add(request);
            }
        }
        submitInBatches(bulk);
    }

    private void bulkSyncUsers(List<Long> userIds) throws Exception {
        Map<Long, Map<String, Object>> rowById = queryRows(selectMapper.selectEsUserIndexDataByIds(userIds));
        BulkRequest bulk = new BulkRequest();
        for (Long userId : userIds) {
            Map<String, Object> row = rowById.get(userId);
            if (row == null) {
                bulk.add(new DeleteRequest(UserIndex.NAME, String.valueOf(userId)));
            } else {
                IndexRequest request = new IndexRequest(UserIndex.NAME).id(String.valueOf(userId)).source(row);
                Object versionObj = row.get("update_time_millis");
                if (versionObj != null) {
                    long version = Long.parseLong(String.valueOf(versionObj));
                    if (version > 0) {
                        request.version(version).versionType(VersionType.EXTERNAL);
                    }
                }
                bulk.add(request);
            }
        }
        submitInBatches(bulk);
    }

    /**
     * 行集合转 id -> row（id 兼容 Long/BigInteger/字符串）
     */
    private Map<Long, Map<String, Object>> queryRows(List<Map<String, Object>> rows) {
        Map<Long, Map<String, Object>> map = new HashMap<>();
        if (rows == null) {
            return map;
        }
        for (Map<String, Object> row : rows) {
            Object idObj = row.get("id");
            if (idObj == null) {
                continue;
            }
            map.put(Long.parseLong(String.valueOf(idObj)), row);
        }
        return map;
    }

    private void submitInBatches(BulkRequest bulk) throws Exception {
        if (bulk.numberOfActions() == 0) {
            return;
        }
        if (bulk.numberOfActions() <= BULK_BATCH_SIZE) {
            handleBulkInsert(bulk, 0, bulk.numberOfActions());
            return;
        }
        for (int from = 0; from < bulk.numberOfActions(); from += BULK_BATCH_SIZE) {
            handleBulkInsert(bulk, from, Math.min(from + BULK_BATCH_SIZE, bulk.numberOfActions()));
        }
    }

    private void handleBulkInsert(BulkRequest bulk, int from, int to) throws Exception {
        BulkRequest part = new BulkRequest();
        for (int i = from; i < to; i++) {
            part.add(bulk.requests().get(i));
        }
        BulkResponse response = restHighLevelClient.bulk(part, RequestOptions.DEFAULT);
        if (response.hasFailures()) {
            boolean hasRealFailure = false;
            StringBuilder errorMsg = new StringBuilder();
            for (BulkItemResponse item : response.getItems()) {
                if (item.isFailed()) {
                    // 外部版本冲突说明当前 ES 已有更新的数据版本，跳过不报错
                    if (item.getFailure().getStatus() == RestStatus.CONFLICT) {
                        log.debug("ES 同步外部版本冲突（已有更新版本），跳过: {}", item.getFailureMessage());
                        continue;
                    }
                    hasRealFailure = true;
                    errorMsg.append(item.getFailureMessage()).append("; ");
                }
            }
            if (hasRealFailure) {
                throw new IllegalStateException("ES 批量同步部分失败: " + errorMsg);
            }
        }
    }
}
