package hk.ljx.framework.mq.support;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * RocketMQ 发送辅助工具
 */
@Slf4j
public class RocketMqHelper {

    private RocketMqHelper() {
    }

    private static Object extractPayload(Object message) {
        return (message instanceof Message<?> msg) ? msg.getPayload() : message;
    }

    /**
     * 同步发送消息，失败抛出异常（由调用方决定回滚与响应，保证"接口成功即消息已入 broker"）。
     */
    public static void syncSend(RocketMQTemplate template, String destination, Object message, String bizDesc) {
        try {
            template.syncSend(destination, extractPayload(message));
        } catch (Exception e) {
            throw new IllegalStateException(bizDesc + " MQ 消息发送失败, destination=" + destination, e);
        }
    }

    /**
     * 同步顺序发送消息，失败抛出异常（由调用方决定回滚与响应）。
     */
    public static void syncSendOrderly(RocketMQTemplate template, String destination, Object message, String hashKey, String bizDesc) {
        try {
            template.syncSendOrderly(destination, extractPayload(message), hashKey);
        } catch (Exception e) {
            throw new IllegalStateException(bizDesc + " MQ 顺序消息发送失败, destination=" + destination + ", hashKey=" + hashKey, e);
        }
    }

    /**
     * 异步单向广播/清理消息（仅记录告警日志）
     */
    public static void asyncSend(RocketMQTemplate template, String destination, Object message, String bizDesc) {
        template.asyncSend(destination, extractPayload(message), new SendCallback() {
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
        Message<?> sendMessage = (payload instanceof Message<?>)
                ? (Message<?>) payload
                : MessageBuilder.withPayload(payload).build();
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
