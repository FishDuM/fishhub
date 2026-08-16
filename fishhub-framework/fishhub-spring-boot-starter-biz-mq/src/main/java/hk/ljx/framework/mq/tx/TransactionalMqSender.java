package hk.ljx.framework.mq.tx;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * 事务消息发送入口：半消息 + 本地事务原子提交。
 *
 * <p>调用约束：
 * <ul>
 *   <li>不得在已开启的数据库事务内调用（否则半消息确认先于业务提交，破坏原子性）；</li>
 *   <li>localTxAction 必须以 @Transactional 方法为事务边界，业务生效时在事务内调用
 *       {@link TxJournalStore#record(String)} 登记提交事实，供崩溃后的回查判定；
 *       幂等跳过时返回 false 且不得登记；</li>
 *   <li>半消息发送失败或本地事务抛异常时，本方法向调用方抛出，业务操作整体失败。</li>
 * </ul>
 */
public class TransactionalMqSender {

    public static final String TX_ID_HEADER = "TX_ID";

    private final RocketMQTemplate rocketMQTemplate;

    public TransactionalMqSender(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    /**
     * @param destination   目标 topic（可带 ":tag"）
     * @param payload       消息体 JSON
     * @param localTxAction 本地事务动作，入参为本次事务的 txId；返回 true 表示业务已生效
     *                      （须在事务内登记 journal），返回 false 表示幂等跳过（半消息回滚丢弃）
     */
    public void sendInTransaction(String destination, String payload, TxLocalTransaction localTxAction) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "sendInTransaction 禁止在已开启的数据库事务内调用，请在事务外发送、由 localTxAction 自建事务边界");
        }
        String txId = UUID.randomUUID().toString().replace("-", "");
        Message<String> message = MessageBuilder.withPayload(payload)
                .setHeader(TX_ID_HEADER, txId)
                .build();
        TxMqBinding binding = new TxMqBinding(txId, localTxAction);
        rocketMQTemplate.sendMessageInTransaction(destination, message, binding);
        RuntimeException failure = binding.failure();
        if (failure != null) {
            throw failure;
        }
    }
}
