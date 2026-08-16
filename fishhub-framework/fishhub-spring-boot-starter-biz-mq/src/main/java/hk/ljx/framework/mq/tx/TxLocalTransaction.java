package hk.ljx.framework.mq.tx;

/**
 * 事务消息的本地事务动作。
 *
 * <p>返回值向监听器声明本地事务的执行结果：
 * <ul>
 *   <li>{@code true}：业务已生效，必须在事务内调用 {@link TxJournalStore#record(String)}
 *       登记提交事实，半消息提交；</li>
 *   <li>{@code false}：幂等跳过（重复消费、关系未变化等），业务未生效、不得登记 journal，
 *       半消息回滚丢弃，事件不对外可见。</li>
 * </ul>
 */
@FunctionalInterface
public interface TxLocalTransaction {

    boolean execute(String txId);
}
