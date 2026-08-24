package hk.ljx.framework.mq.idempotent;

import cn.hutool.crypto.digest.DigestUtil;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.function.Function;

/**
 * MQ 消费幂等执行器：消息身份（topic+原文）哈希后写 t_mq_consume_record，
 * 与业务动作同事务；重复投递/并发重投只允许一次生效。
 */
public class MqIdempotentExecutor {

    private final MqConsumeRecordStore store;
    private final TransactionTemplate transactionTemplate;

    public MqIdempotentExecutor(MqConsumeRecordStore store, TransactionTemplate transactionTemplate) {
        this.store = store;
        this.transactionTemplate = transactionTemplate;
    }

    public boolean execute(String consumerGroup, String messageIdentity, Runnable databaseAction) {
        String messageKey = DigestUtil.sha256Hex(messageIdentity);
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            if (store.exists(consumerGroup, messageKey) > 0) {
                return false;
            }
            try {
                store.insert(consumerGroup, messageKey);
            } catch (DuplicateKeyException e) {
                return false;
            }
            databaseAction.run();
            return true;
        }));
    }

    /**
     * 批量事件级幂等：以每条消息身份独立判重，只对本次新增的键执行 freshAction。
     * 并发抢占导致插入行数不匹配时回滚整批，交由消息重投收敛（重投后已存在键被跳过）。
     *
     * @param consumerGroup      消费组
     * @param messageIdentities  本批消息身份（每条唯一）
     * @param freshAction        新增键集合上的业务动作，入参为本次新增的 sha256 键；返回 true 表示已应用
     * @return 本批是否应用了业务（false 表示全部重复投递）
     */
    public boolean executeBatch(String consumerGroup, List<String> messageIdentities,
                                Function<List<String>, Boolean> freshAction) {
        if (messageIdentities == null || messageIdentities.isEmpty()) {
            return false;
        }
        List<String> messageKeys = messageIdentities.stream()
                .map(DigestUtil::sha256Hex)
                .toList();
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            List<String> existing = store.findExisting(consumerGroup, messageKeys);
            List<String> freshKeys = messageKeys.stream()
                    .filter(key -> !existing.contains(key))
                    .toList();
            if (freshKeys.isEmpty()) {
                return false;
            }
            int inserted = store.insertIgnoreBatch(consumerGroup, freshKeys);
            if (inserted != freshKeys.size()) {
                // 并发窗口内部分键被其他实例写入：抛出异常触发事务回滚，并驱动 MQ 稍后重投收敛
                throw new IllegalStateException("批量幂等插入并发冲突 (expected " + freshKeys.size() + ", inserted " + inserted + ")，回滚触发 MQ 重投");
            }
            return Boolean.TRUE.equals(freshAction.apply(freshKeys));
        }));
    }
}
