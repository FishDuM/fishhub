package hk.ljx.framework.mq.consumer;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.springframework.beans.factory.DisposableBean;

/**
 * 批量 Push 消费者包装类
 */
public final class BatchPushConsumer implements AutoCloseable, DisposableBean {

    private final DefaultMQPushConsumer consumer;

    BatchPushConsumer(DefaultMQPushConsumer consumer) {
        this.consumer = consumer;
    }

    /**
     * 底层 RocketMQ 消费者（仅在需要深度定制时使用）。
     */
    public DefaultMQPushConsumer getConsumer() {
        return consumer;
    }

    /**
     * 关闭消费者。
     */
    public void shutdown() {
        try {
            consumer.shutdown();
        } catch (Exception ignored) {
            // 关闭阶段忽略异常
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    @Override
    public void destroy() {
        shutdown();
    }
}
