package hk.ljx.framework.mq.consumer;

import jakarta.annotation.PreDestroy;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.remoting.protocol.heartbeat.MessageModel;
import org.springframework.beans.factory.annotation.Value;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 批量 Push 消费者工厂
 */
public class BatchConsumerFactory {

    /**
     * 消费模式：并发（默认）或顺序。
     */
    public enum Mode {
        CONCURRENTLY,
        ORDERLY
    }

    private final String namesrvAddr;
    private final Set<DefaultMQPushConsumer> startedConsumers = ConcurrentHashMap.newKeySet();

    public BatchConsumerFactory(@Value("${rocketmq.name-server}") String namesrvAddr) {
        this.namesrvAddr = namesrvAddr;
    }

    /**
     * 创建并启动一个批量 Push 消费者。
     *
     * @param group             消费组
     * @param topic             订阅的 Topic
     * @param tag               订阅的 Tag（"*" 表示全部）
     * @param batchMaxSize      每批最大消息数
     * @param maxReconsumeTimes 最大重试次数（&lt;=0 表示使用默认值）
     * @param mode              并发 / 顺序消费模式
     * @param handler           批量消息处理器
     * @return 消费者句柄（生命周期由本工厂托管）
     */
    public BatchPushConsumer create(String group, String topic, String tag, int batchMaxSize,
                                    int maxReconsumeTimes, Mode mode, BatchMessageHandler handler) throws MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(group);

        // 设置 RocketMQ 的 NameServer 地址
        consumer.setNamesrvAddr(namesrvAddr);

        // 订阅指定的主题，并设置主题的订阅规则（"*" 表示订阅所有标签的消息）
        consumer.subscribe(topic, tag);

        // 设置消费者消费消息的起始位置
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);

        // 集群模式
        consumer.setMessageModel(MessageModel.CLUSTERING);

        // 批量拉取大小
        consumer.setConsumeMessageBatchMaxSize(batchMaxSize);

        if (maxReconsumeTimes > 0) {
            consumer.setMaxReconsumeTimes(maxReconsumeTimes);
        }

        // 注册批量消息监听器，并把业务返回的布尔结果翻译成 RocketMQ 消费状态
        if (mode == Mode.ORDERLY) {
            consumer.registerMessageListener((MessageListenerOrderly) (msgs, context) ->
                    handler.handle(msgs) ? ConsumeOrderlyStatus.SUCCESS : ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT);
        } else {
            consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) ->
                    handler.handle(msgs) ? ConsumeConcurrentlyStatus.CONSUME_SUCCESS : ConsumeConcurrentlyStatus.RECONSUME_LATER);
        }

        startedConsumers.add(consumer);
        consumer.start();
        return new BatchPushConsumer(consumer);
    }

    /**
     * 应用关闭时统一关闭所有由本工厂创建的消费者。
     */
    @PreDestroy
    public void shutdownAll() {
        startedConsumers.forEach(consumer -> {
            try {
                consumer.shutdown();
            } catch (Exception ignored) {
                // 关闭阶段忽略异常
            }
        });
        startedConsumers.clear();
    }
}
