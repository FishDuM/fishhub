package hk.ljx.fishhub.user.relation.biz.retry;

import cn.hutool.crypto.digest.DigestUtil;
import hk.ljx.fishhub.user.relation.biz.domain.dataobject.MqSendFailureDO;
import hk.ljx.fishhub.user.relation.biz.domain.mapper.MqSendFailureMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
public class ReliableMqOutbox {

    @Resource
    private MqSendFailureMapper mqSendFailureMapper;
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    public void enqueue(String topic, String body, String orderingKey) {
        mqSendFailureMapper.insertPending(MqSendFailureDO.builder()
                .messageKey(messageKey(topic, body))
                .topic(topic)
                .orderingKey(orderingKey)
                .body(body)
                .nextRetryTime(LocalDateTime.now())
                .build());
    }

    public void sendNow(String topic, String body, String orderingKey) {
        String messageKey = messageKey(topic, body);
        if (mqSendFailureMapper.existsEarlierPending(orderingKey, messageKey) > 0) {
            return;
        }
        try {
            rocketMQTemplate.syncSendOrderly(topic, MessageBuilder.withPayload(body).build(), orderingKey);
            mqSendFailureMapper.deleteByMessageKey(messageKey);
        } catch (Exception e) {
            log.warn("计数消息即时发送失败，已保留在 outbox 中等待补发，topic={}", topic, e);
        }
    }

    private String messageKey(String topic, String body) {
        return DigestUtil.sha256Hex(topic + ':' + body);
    }
}
