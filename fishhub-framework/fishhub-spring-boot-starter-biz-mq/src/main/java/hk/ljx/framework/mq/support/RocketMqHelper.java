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
     * 同步发送消息，失败抛出异常（由调用方决定回滚与响应，保证"接口成功即消息已入 broker"）。
     */
    public static void syncSend(RocketMQTemplate template, String destination, Object message, String bizDesc) {
        Object sendMessage = resolvePayload(message);
        try {
            template.syncSend(destination, sendMessage);
        } catch (Exception e) {
            throw new IllegalStateException(bizDesc + " MQ 消息发送失败, destination=" + destination, e);
        }
    }

    /**
     * 同步顺序发送消息，失败抛出异常（由调用方决定回滚与响应）。
     */
    public static void syncSendOrderly(RocketMQTemplate template, String destination, Object message, String hashKey, String bizDesc) {
        Object sendMessage = resolvePayload(message);
        try {
            template.syncSendOrderly(destination, sendMessage, hashKey);
        } catch (Exception e) {
            throw new IllegalStateException(bizDesc + " MQ 顺序消息发送失败, destination=" + destination + ", hashKey=" + hashKey, e);
        }
    }

    private static Object resolvePayload(Object message) {
        if (message instanceof org.springframework.messaging.Message<?> springMessage) {
            return springMessage.getPayload();
        }
        return message;
    }

    /**
     * 异步单向广播/清理消息（仅记录告警日志）
     */
    public static void asyncSend(RocketMQTemplate template, String destination, Object message, String bizDesc) {
        Object sendMessage = resolvePayload(message);
        template.asyncSend(destination, sendMessage, new SendCallback() {
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
        // 延迟消息走 Message 重载：RocketMQTemplate 仅提供 (destination, Message<?>, callback, timeout, delayLevel)
        org.springframework.messaging.Message<?> sendMessage = (payload instanceof org.springframework.messaging.Message<?>)
                ? (org.springframework.messaging.Message<?>) payload
                : org.springframework.messaging.support.MessageBuilder.withPayload(payload).build();
        template.asyncSend(destination, sendMessage, new SendCallback() {
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
