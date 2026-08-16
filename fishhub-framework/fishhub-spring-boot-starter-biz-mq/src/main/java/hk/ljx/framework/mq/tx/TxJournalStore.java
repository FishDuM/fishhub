package hk.ljx.framework.mq.tx;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 事务消息本地事务日志（t_tx_journal）读写入口。
 *
 * <p>record() 必须在业务方法的 @Transactional 边界内调用，使 journal 行与业务写入
 * 同事务提交/回滚——这是回查能正确判定"本地事务是否已提交"的唯一依据。
 * 表无业务字段，仅以 tx_id 存在性表达提交事实，由 {@link TxJournalPurgeJob} 滚动清理。
 */
@RequiredArgsConstructor
public class TxJournalStore {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 在业务事务内登记本次事务消息的提交事实。
     */
    public void record(String txId) {
        jdbcTemplate.update("insert ignore into t_tx_journal (tx_id) values (?)", txId);
    }

    /**
     * 回查判定：journal 行存在视为本地事务已提交。
     */
    public boolean exists(String txId) {
        if (txId == null || txId.isEmpty()) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "select count(1) from t_tx_journal where tx_id = ?", Integer.class, txId);
        return count != null && count > 0;
    }
}
