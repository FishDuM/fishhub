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
            try {
                store.insert(consumerGroup, messageKey);
            } catch (DuplicateKeyException e) {
                // 唯一索引冲突：表示消息已处理过，幂等拦截
                return false;
            }
            databaseAction.run();
            return true;
        }));
    }

    /**
     * 批量事件级幂等：以每条消息身份独立判重，只对本次新增的键执行 freshAction。
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
            store.insertIgnoreBatch(consumerGroup, freshKeys);
            return Boolean.TRUE.equals(freshAction.apply(freshKeys));
        }));
    }
}
