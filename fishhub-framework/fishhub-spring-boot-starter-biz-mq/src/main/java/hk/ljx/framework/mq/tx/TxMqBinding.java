package hk.ljx.framework.mq.tx;

/**
 * sendInTransaction 与本地事务监听器之间的同线程传递载体。
 * rocketmq-client 在调用线程内同步执行 executeLocalTransaction，
 * 因此可以在发送返回后将本地事务的失败原样抛回业务调用方。
 */
class TxMqBinding {

    private final String txId;
    private final TxLocalTransaction localTx;

    private volatile RuntimeException failure;

    TxMqBinding(String txId, TxLocalTransaction localTx) {
        this.txId = txId;
        this.localTx = localTx;
    }

    String txId() {
        return txId;
    }

    boolean execute() {
        return localTx.execute(txId);
    }

    void captureFailure(RuntimeException e) {
        failure = e;
    }

    RuntimeException failure() {
        return failure;
    }
}
