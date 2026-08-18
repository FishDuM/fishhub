package hk.ljx.framework.mq.idempotent;

import cn.hutool.crypto.digest.DigestUtil;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;

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
}
