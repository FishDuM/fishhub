package hk.ljx.fishhub.search.biz.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import com.google.common.collect.Lists;
import hk.ljx.fishhub.search.biz.domain.mapper.SelectMapper;
import hk.ljx.fishhub.search.biz.index.NoteIndex;
import hk.ljx.fishhub.search.biz.index.UserIndex;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    /** 同步失败重试队列（Redis ZSet，score=下次重试时间戳，无限重试直到成功） */
    private static final String ES_RETRY_NOTE_KEY = "es:retry:note";
    private static final String ES_RETRY_USER_KEY = "es:retry:user";
    private static final long RETRY_DELAY_MS = 30_000L;

    private final RestHighLevelClient restHighLevelClient;
    private final SelectMapper selectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    private final Set<Long> pendingNoteIds = ConcurrentHashMap.newKeySet();
    private final Set<Long> pendingUserIds = ConcurrentHashMap.newKeySet();

    /**
     * 笔记计数变化后提交同步（去重合并）
     */
    public void submitNote(Long noteId) {
        if (noteId != null && pendingNoteIds.add(noteId) && pendingNoteIds.size() >= FLUSH_CAP) {
            flushNotes();
        }
    }

    /**
     * 用户计数变化后提交同步（去重合并）
     */
    public void submitUser(Long userId) {
        if (userId != null && pendingUserIds.add(userId) && pendingUserIds.size() >= FLUSH_CAP) {
            flushUsers();
        }
    }

    /**
     * 批量提交笔记同步（用户资料变更导致该用户全部笔记文档需要重建）。
     */
    public void submitNoteIds(Collection<Long> noteIds) {
        if (CollUtil.isNotEmpty(noteIds)) {
            pendingNoteIds.addAll(noteIds);
            if (pendingNoteIds.size() >= FLUSH_CAP) {
                flushNotes();
            }
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
        doFlush(drain(pendingNoteIds), selectMapper::selectEsNoteIndexDataByIds, NoteIndex.NAME, ES_RETRY_NOTE_KEY);
    }

    public void flushUsers() {
        doFlush(drain(pendingUserIds), selectMapper::selectEsUserIndexDataByIds, UserIndex.NAME, ES_RETRY_USER_KEY);
    }

    private void doFlush(List<Long> ids, Function<List<Long>, List<Map<String, Object>>> dbLoader, String indexName, String retryKey) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        try {
            syncBatch(ids, dbLoader, indexName);
        } catch (Exception e) {
            log.error("ES 索引批量同步失败, indexName={}, idsSize={}", indexName, ids.size(), e);
            enqueueRetry(retryKey, ids);
        }
    }

    /**
     * 重试到期的失败同步（由 EsSyncRetryJob 定时触发）。
     */
    public void retryPendingNotes() {
        retryPending(ES_RETRY_NOTE_KEY, ids -> syncBatch(ids, selectMapper::selectEsNoteIndexDataByIds, NoteIndex.NAME));
    }

    public void retryPendingUsers() {
        retryPending(ES_RETRY_USER_KEY, ids -> syncBatch(ids, selectMapper::selectEsUserIndexDataByIds, UserIndex.NAME));
    }

    private void syncBatch(List<Long> ids, Function<List<Long>, List<Map<String, Object>>> dbLoader, String indexName) throws Exception {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        Map<Long, Map<String, Object>> rowById = queryRows(dbLoader.apply(ids));
        BulkRequest bulk = new BulkRequest();
        for (Long id : ids) {
            Map<String, Object> row = rowById.get(id);
            if (row == null) {
                // 笔记或用户已不可检索（删除/下架/私密），从索引移除
                bulk.add(new DeleteRequest(indexName, String.valueOf(id)));
            } else {
                bulk.add(new IndexRequest(indexName).id(String.valueOf(id)).source(row));
            }
        }
        submitInBatches(bulk);
    }

    private void retryPending(String retryKey, EsBulkSync sync) {
        Set<String> dueIds = stringRedisTemplate.opsForZSet()
                .rangeByScore(retryKey, 0, System.currentTimeMillis());
        if (CollUtil.isEmpty(dueIds)) {
            return;
        }
        List<Long> ids = new ArrayList<>();
        List<String> dirtyMembers = new ArrayList<>();
        for (String member : dueIds) {
            try {
                ids.add(Long.valueOf(member));
            } catch (NumberFormatException e) {
                dirtyMembers.add(member);
            }
        }
        if (!dirtyMembers.isEmpty()) {
            stringRedisTemplate.opsForZSet().remove(retryKey, dirtyMembers.toArray());
            log.warn("ES 重试队列存在脏数据，已移除, key={}, count={}", retryKey, dirtyMembers.size());
        }
        if (ids.isEmpty()) {
            return;
        }
        try {
            sync.sync(ids);
            stringRedisTemplate.opsForZSet().remove(retryKey, dueIds.toArray());
        } catch (Exception e) {
            log.warn("ES 重试队列同步仍失败，推迟下次重试时间, key={}, idsSize={}", retryKey, ids.size(), e);
            enqueueRetry(retryKey, ids);
        }
    }

    private void enqueueRetry(String retryKey, List<Long> ids) {
        try {
            long next = System.currentTimeMillis() + RETRY_DELAY_MS;
            for (Long id : ids) {
                stringRedisTemplate.opsForZSet().add(retryKey, String.valueOf(id), next);
            }
        } catch (Exception redisEx) {
            log.error("ES 重试队列写入 Redis 失败, key={}", retryKey, redisEx);
        }
    }

    @FunctionalInterface
    private interface EsBulkSync {
        void sync(List<Long> ids) throws Exception;
    }

    private static List<Long> drain(Set<Long> pending) {
        if (pending.isEmpty()) {
            return List.of();
        }
        List<Long> batch = new ArrayList<>(pending);
        pending.removeAll(batch);
        return batch;
    }

    private Map<Long, Map<String, Object>> queryRows(List<Map<String, Object>> rows) {
        if (CollUtil.isEmpty(rows)) {
            return Map.of();
        }
        return rows.stream()
                .filter(row -> row.get("id") != null)
                .collect(Collectors.toMap(
                        row -> Convert.toLong(row.get("id")),
                        Function.identity(),
                        (a, b) -> a
                ));
    }

    private void submitInBatches(BulkRequest bulk) throws Exception {
        if (bulk.numberOfActions() == 0) {
            return;
        }
        for (List<DocWriteRequest<?>> batch : Lists.partition(bulk.requests(), BULK_BATCH_SIZE)) {
            BulkRequest part = new BulkRequest();
            batch.forEach(part::add);
            BulkResponse response = restHighLevelClient.bulk(part, RequestOptions.DEFAULT);
            if (response.hasFailures()) {
                String errorMsg = Arrays.stream(response.getItems())
                        .filter(BulkItemResponse::isFailed)
                        .map(BulkItemResponse::getFailureMessage)
                        .collect(Collectors.joining("; "));
                if (!errorMsg.isEmpty()) {
                    throw new IllegalStateException("ES 批量同步部分失败: " + errorMsg);
                }
            }
        }
    }
}
