package hk.ljx.fishhub.comment.biz.retry;

import cn.hutool.crypto.digest.DigestUtil;
import hk.ljx.fishhub.comment.biz.domain.dataobject.MqSendFailureDO;
import hk.ljx.fishhub.comment.biz.domain.mapper.MqSendFailureMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;


@Component
@Slf4j
public class SendMqRetryHelper {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    @Resource
    private RocketMQTemplate rocketMQTemplate;
    @Resource
    private MqSendFailureMapper mqSendFailureMapper;

    /**
     * 异步发送 MQ
     * @param topic
     */
    public void sendReliable(String topic, String body) {
        log.info("==> 开始发送 MQ, Topic: {}", topic);

        // 构建消息对象，并将 DTO 转成 Json 字符串设置到消息体中
        Message<String> message = MessageBuilder.withPayload(body)
                .build();

        try {
            rocketMQTemplate.syncSend(topic, message);
        } catch (Exception e) {
            // Broker 暂时不可用时先持久化，定时任务继续补发；持久化失败则向调用方抛错。
            fallback(e, topic, body);
        }
    }

    /**
     * 必须在业务数据库事务中调用，使业务事实与待发送事件一起提交。
     */
    public void enqueue(String topic, String body) {
        mqSendFailureMapper.insertPending(MqSendFailureDO.builder()
                .messageKey(messageKey(topic, body))
                .topic(topic)
                .body(body)
                .nextRetryTime(LocalDateTime.now())
                .build());
    }

    /**
     * 仅在业务事务提交后调用。失败时保留 outbox 记录等待定时补发。
     */
    public void sendNow(String topic, String body) {
        try {
            rocketMQTemplate.syncSend(topic, MessageBuilder.withPayload(body).build());
            mqSendFailureMapper.deleteByMessageKey(messageKey(topic, body));
        } catch (Exception e) {
            log.warn("MQ 事件即时发送失败，已保留在 outbox 中，topic={}", topic, e);
        }
    }

    /**
     * 兜底方案: 将发送失败的 MQ 写入数据库，之后，通过定时任务扫表，将发送失败的 MQ 再次发送，最终发送成功后，将该记录物理删除
     */
    private void fallback(Exception e, String topic, String bodyJson) {
        log.error("MQ 发送失败，已写入 Outbox，topic={}, payloadSize={}", topic, bodyJson.length(), e);

        String errorMessage = e.getMessage();
        errorMessage = StringUtils.abbreviate(
                errorMessage == null ? e.getClass().getName() : errorMessage,
                MAX_ERROR_MESSAGE_LENGTH);
        mqSendFailureMapper.insertOrRefresh(MqSendFailureDO.builder()
                .messageKey(messageKey(topic, bodyJson))
                .topic(topic)
                .body(bodyJson)
                .nextRetryTime(LocalDateTime.now())
                .lastError(errorMessage)
                .build());
    }

    private String messageKey(String topic, String body) {
        return DigestUtil.sha256Hex(topic + ':' + body);
    }
}
