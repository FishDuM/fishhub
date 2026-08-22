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
     * 异步发送消息，失败后同步重试一次；仍失败则回滚。
     */
    public static void asyncSendWithRetry(RocketMQTemplate template, String destination, Object message, String bizDesc, Runnable rollback) {
        Object sendMessage = resolvePayload(message);
        try {
            template.asyncSend(destination, sendMessage, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                }

                @Override
                public void onException(Throwable e) {
                    try {
                        template.syncSend(destination, sendMessage);
                    } catch (Exception retryEx) {
                        log.error("{} MQ 消息发送失败, destination={}", bizDesc, destination, retryEx);
                        runRollback(bizDesc, destination, null, rollback);
                    }
                }
            });
        } catch (Exception e) {
            log.error("{} MQ 异步发送异常，尝试同步重试, destination={}", bizDesc, destination, e);
            try {
                template.syncSend(destination, sendMessage);
            } catch (Exception retryEx) {
                log.error("{} MQ 消息发送失败, destination={}", bizDesc, destination, retryEx);
                runRollback(bizDesc, destination, null, rollback);
            }
        }
    }

    /**
     * 异步顺序发送消息，失败后同步重试一次；仍失败则回滚。
     */
    public static void asyncSendOrderlyWithRetry(RocketMQTemplate template, String destination, Object message, String hashKey, String bizDesc, Runnable rollback) {
        Object sendMessage = resolvePayload(message);
        try {
            template.asyncSendOrderly(destination, sendMessage, hashKey, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                }

                @Override
                public void onException(Throwable e) {
                    try {
                        template.syncSendOrderly(destination, sendMessage, hashKey);
                    } catch (Exception retryEx) {
                        log.error("{} MQ 顺序消息发送失败, destination={}, hashKey={}", bizDesc, destination, hashKey, retryEx);
                        runRollback(bizDesc, destination, hashKey, rollback);
                    }
                }
            });
        } catch (Exception e) {
            log.error("{} MQ 顺序异步发送异常，尝试同步重试, destination={}, hashKey={}", bizDesc, destination, hashKey, e);
            try {
                template.syncSendOrderly(destination, sendMessage, hashKey);
            } catch (Exception retryEx) {
                log.error("{} MQ 顺序消息发送失败, destination={}, hashKey={}", bizDesc, destination, hashKey, retryEx);
                runRollback(bizDesc, destination, hashKey, rollback);
            }
        }
    }

    private static Object resolvePayload(Object message) {
        if (message instanceof org.springframework.messaging.Message<?> springMessage) {
            return springMessage.getPayload();
        }
        return message;
    }

    private static void runRollback(String bizDesc, String destination, String hashKey, Runnable rollback) {
        if (rollback == null) {
            return;
        }
        try {
            rollback.run();
        } catch (Exception rollbackEx) {
            if (hashKey == null) {
                log.error("{} MQ 发送失败后回滚缓存失败, destination={}", bizDesc, destination, rollbackEx);
            } else {
                log.error("{} MQ 顺序消息发送失败后回滚缓存失败, destination={}, hashKey={}", bizDesc, destination, hashKey, rollbackEx);
            }
        }
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
