package hk.ljx.fishhub.search.biz.service;

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private final Set<Long> pendingNoteIds = new HashSet<>();
    private final Set<Long> pendingUserIds = new HashSet<>();
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
     * 批量提交笔记同步（用户资料变更导致该用户全部笔记文档需要重建）。
     */
    public void submitNoteIds(java.util.Collection<Long> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) {
            return;
        }
        boolean fire;
        synchronized (pendingNoteIds) {
            pendingNoteIds.addAll(noteIds);
            fire = pendingNoteIds.size() >= FLUSH_CAP;
        }
        if (fire) {
            flushNotes();
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
            } catch (Exception e) {
                log.error("ES 笔记索引批量同步失败, idsSize={}", ids.size(), e);
                // 失败 ID 写入 Redis ZSet，由定时任务无限重试，ES 恢复后自动追平
                enqueueRetry(ES_RETRY_NOTE_KEY, ids);
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
            } catch (Exception e) {
                log.error("ES 用户索引批量同步失败, idsSize={}", ids.size(), e);
                enqueueRetry(ES_RETRY_USER_KEY, ids);
            }
        }
    }

    /**
     * 重试到期的失败同步（由 EsSyncRetryJob 定时触发）。
     */
    public void retryPendingNotes() {
        retryPending(ES_RETRY_NOTE_KEY, this::bulkSyncNotes);
    }

    public void retryPendingUsers() {
        retryPending(ES_RETRY_USER_KEY, this::bulkSyncUsers);
    }

    private void retryPending(String retryKey, EsBulkSync sync) {
        Set<String> dueIds = stringRedisTemplate.opsForZSet()
                .rangeByScore(retryKey, 0, System.currentTimeMillis());
        if (dueIds == null || dueIds.isEmpty()) {
            return;
        }
        // 过滤脏数据：非数字 member 无法重试，直接移除，避免单个脏数据卡死整个重试队列
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
            // 成功后移除，避免重复重试
            stringRedisTemplate.opsForZSet().remove(retryKey, dueIds.toArray());
        } catch (Exception e) {
            log.warn("ES 重试队列同步仍失败, key={}, idsSize={}", retryKey, ids.size(), e);
            // 保持 ZSet 中的 score 不变，下次调度继续重试（无限次）
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
                // 每次同步全量重查 DB 最新值，last-write-wins 天然收敛（不用主表 update_time 做外部版本：
                // 计数变更不碰主表，版本恒等会导致 CONFLICT 被跳过、计数永不更新）
                IndexRequest request = new IndexRequest(NoteIndex.NAME).id(String.valueOf(noteId)).source(row);
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
                bulk.add(request);
            }
        }
        submitInBatches(bulk);
    }

    /**
     * 行集合转 id -> row（id 兼容 Long/BigInteger/字符串）
     */
    private Map<Long, Map<String, Object>> queryRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
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
