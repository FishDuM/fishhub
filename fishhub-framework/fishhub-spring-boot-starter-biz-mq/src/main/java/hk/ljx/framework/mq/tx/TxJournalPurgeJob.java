package hk.ljx.framework.mq.tx;

import hk.ljx.framework.mq.tx.mapper.TxJournalDOMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * t_tx_journal 滚动清理（统一由 MyBatis 实现）。
 * 回查窗口为分钟级（broker 默认 6s 起查、最多 15 次），
 * 保留 24 小时已有近百倍裕量；过期行仅是死数据，删除不影响任何在途事务。
 */
@Slf4j
public class TxJournalPurgeJob {

    private static final int BATCH_SIZE = 2000;

    private final TxJournalDOMapper mapper;
    private final int retentionHours;

    public TxJournalPurgeJob(TxJournalDOMapper mapper, int retentionHours) {
        this.mapper = mapper;
        this.retentionHours = retentionHours;
    }

    @Scheduled(cron = "${mq.tx-journal.purge-cron:0 40 4 * * ?}")
    public void purge() {
        int total = 0;
        int deleted;
        do {
            deleted = mapper.purgeOlderThanHours(retentionHours, BATCH_SIZE);
            total += deleted;
        } while (deleted == BATCH_SIZE);
        if (total > 0) {
            log.info("t_tx_journal 清理完成, 删除 {} 行, 保留 {} 小时", total, retentionHours);
        }
    }
}
