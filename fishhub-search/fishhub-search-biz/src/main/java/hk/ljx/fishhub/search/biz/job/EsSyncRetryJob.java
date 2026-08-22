package hk.ljx.fishhub.search.biz.job;

import hk.ljx.fishhub.search.biz.service.EsIndexSyncAggregator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ES 同步失败重试任务：消费 Redis ZSet 中的失败 ID，持续重试直到成功（ES 恢复后自动追平）。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EsSyncRetryJob {

    private static final long RETRY_INTERVAL_MS = 30_000L;

    private final EsIndexSyncAggregator esIndexSyncAggregator;

    @Scheduled(fixedDelay = RETRY_INTERVAL_MS)
    public void retry() {
        try {
            esIndexSyncAggregator.retryPendingNotes();
        } catch (Exception e) {
            log.error("ES 笔记重试队列处理异常", e);
        }
        try {
            esIndexSyncAggregator.retryPendingUsers();
        } catch (Exception e) {
            log.error("ES 用户重试队列处理异常", e);
        }
    }
}
