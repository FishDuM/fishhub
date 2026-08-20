package hk.ljx.framework.mq.support;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;

/**
 * RocketMQ 发送辅助工具
 */
@Slf4j
public class RocketMqHelper {

    private RocketMqHelper() {
    }

    /**
     * 异步发送消息（失败时自动同步重试一次）
     */
    public static void asyncSendWithRetry(RocketMQTemplate template, String destination, Object message, String bizDesc) {
        template.asyncSend(destination, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
            }

            @Override
            public void onException(Throwable e) {
                try {
                    template.syncSend(destination, message);
                } catch (Exception retryEx) {
                    log.error("{} MQ 消息重试发送失败, destination={}", bizDesc, destination, retryEx);
                }
            }
        });
    }

    /**
     * 异步顺序发送消息（失败时自动同步重试一次）
     */
    public static void asyncSendOrderlyWithRetry(RocketMQTemplate template, String destination, Object message, String hashKey, String bizDesc) {
        template.asyncSendOrderly(destination, message, hashKey, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
            }

            @Override
            public void onException(Throwable e) {
                try {
                    template.syncSendOrderly(destination, message, hashKey);
                } catch (Exception retryEx) {
                    log.error("{} MQ 顺序消息重试发送失败, destination={}, hashKey={}", bizDesc, destination, hashKey, retryEx);
                }
            }
        });
    }

    /**
     * 异步单向广播/清理消息（仅记录告警日志）
     */
    public static void asyncSend(RocketMQTemplate template, String destination, Object message, String bizDesc) {
        template.asyncSend(destination, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
            }

            @Override
            public void onException(Throwable e) {
                log.warn("{} MQ 消息发送失败, destination={}", bizDesc, destination, e);
            }
        });
    }

    /**
     * 异步发送延迟消息（仅记录告警日志）
     */
    public static void asyncSendDelay(RocketMQTemplate template, String destination, Object payload, long timeout, int delayLevel, String bizDesc) {
        org.springframework.messaging.Message<?> message = (payload instanceof org.springframework.messaging.Message<?>)
                ? (org.springframework.messaging.Message<?>) payload
                : org.springframework.messaging.support.MessageBuilder.withPayload(payload).build();
        template.asyncSend(destination, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
            }

            @Override
            public void onException(Throwable e) {
                log.warn("{} 延迟 MQ 消息发送失败, destination={}", bizDesc, destination, e);
            }
        }, timeout, delayLevel);
    }
}
