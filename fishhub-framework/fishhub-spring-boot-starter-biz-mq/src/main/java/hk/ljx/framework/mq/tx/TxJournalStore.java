package hk.ljx.framework.mq.tx;

import hk.ljx.framework.mq.tx.mapper.TxJournalDOMapper;
import lombok.RequiredArgsConstructor;

/**
 * 事务消息本地事务日志（t_tx_journal）读写入口（统一由 MyBatis 实现）。
 *
 * <p>record() 必须在业务方法的 @Transactional 边界内调用，使 journal 行与业务写入
 * 同事务提交/回滚——这是回查能正确判定"本地事务是否已提交"的唯一依据。
 * 表无业务字段，仅以 tx_id 存在性表达提交事实，由 {@link TxJournalPurgeJob} 滚动清理。
 */
@RequiredArgsConstructor
public class TxJournalStore {

    private final TxJournalDOMapper mapper;

    /**
     * 在业务事务内登记本次事务消息的提交事实。
     */
    public void record(String txId) {
        mapper.insertIgnore(txId);
    }

    /**
     * 回查判定：journal 行存在视为本地事务已提交。
     */
    public boolean exists(String txId) {
        if (txId == null || txId.isEmpty()) {
            return false;
        }
        return mapper.countByTxId(txId) > 0;
    }
}
