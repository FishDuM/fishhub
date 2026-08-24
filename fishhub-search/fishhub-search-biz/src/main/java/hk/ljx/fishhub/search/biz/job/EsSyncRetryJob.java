package hk.ljx.fishhub.search.biz.job;

import hk.ljx.fishhub.search.biz.service.EsIndexSyncAggregator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ES 同步失败重试任务：消费 Redis ZSet 中的失败 ID，持续重试直到成功（ES 恢复后自动追平）。
 */
@Component
public class EsSyncRetryJob {

    private static final Logger log = LoggerFactory.getLogger(EsSyncRetryJob.class);
    private static final long RETRY_INTERVAL_MS = 30_000L;

    private final EsIndexSyncAggregator esIndexSyncAggregator;

    public EsSyncRetryJob(EsIndexSyncAggregator esIndexSyncAggregator) {
        this.esIndexSyncAggregator = esIndexSyncAggregator;
    }

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
