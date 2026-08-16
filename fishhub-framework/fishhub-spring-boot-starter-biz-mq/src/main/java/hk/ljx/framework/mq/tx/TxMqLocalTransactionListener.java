package hk.ljx.framework.mq.tx;

import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;

/**
 * 通用事务消息监听器：执行随消息传入的本地事务动作，以 journal 行存在性回查。
 *
 * <p>rocketmq-spring 一个 RocketMQTemplate 只允许绑定一个事务监听器，
 * 因此本类不做任何业务分发，业务语义全部由发送方通过 {@link TxMqBinding} 闭包携带。
 */
@Slf4j
@RequiredArgsConstructor
@RocketMQTransactionListener
public class TxMqLocalTransactionListener implements RocketMQLocalTransactionListener {

    private final TxJournalStore txJournalStore;

    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        if (!(arg instanceof TxMqBinding binding)) {
            log.error("事务消息缺少本地事务绑定，回滚, destination={}", msg.getHeaders().get("destination"));
            return RocketMQLocalTransactionState.ROLLBACK;
        }
        boolean applied;
        try {
            applied = binding.execute();
        } catch (RuntimeException e) {
            // rocketmq-client 会吞掉本地事务异常，这里先暂存，由发送方在发送返回后原样抛出。
            binding.captureFailure(e);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
        if (!applied) {
            // 重复消费等幂等跳过：业务未生效，半消息回滚丢弃，事件不对外可见。
            log.info("本地事务幂等跳过，事务消息已回滚, txId={}", binding.txId());
            return RocketMQLocalTransactionState.ROLLBACK;
        }
        if (!txJournalStore.exists(binding.txId())) {
            // localTx 忘记在事务内 record(txId)：立即回滚并显式报错，
            // 否则崩溃后回查会误判为回滚，消息悄无声息丢失。
            log.error("本地事务未登记 journal，事务消息已回滚, txId={}", binding.txId());
            return RocketMQLocalTransactionState.ROLLBACK;
        }
        return RocketMQLocalTransactionState.COMMIT;
    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        Object txId = msg.getHeaders().get(TransactionalMqSender.TX_ID_HEADER);
        boolean committed = txJournalStore.exists(txId == null ? null : String.valueOf(txId));
        if (!committed) {
            // 半消息超时未确认（生产者崩溃或本地事务回滚），按回滚丢弃。
            log.warn("事务消息回查未命中 journal，回滚丢弃, txId={}", txId);
        }
        return committed ? RocketMQLocalTransactionState.COMMIT : RocketMQLocalTransactionState.ROLLBACK;
    }
}
