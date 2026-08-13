package hk.ljx.fishhub.note.biz.retry;

import cn.hutool.crypto.digest.DigestUtil;
import hk.ljx.fishhub.note.biz.domain.dataobject.MqSendFailureDO;
import hk.ljx.fishhub.note.biz.domain.mapper.MqSendFailureMapper;
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

    /**
     * 必须在业务数据库事务中调用，使业务事实与待发送事件一起提交。
     */
    public void enqueue(String destination, String body) {
        mqSendFailureMapper.insertPending(MqSendFailureDO.builder()
                .messageKey(messageKey(destination, body))
                .topic(destination)
                .body(body)
                .nextRetryTime(LocalDateTime.now())
                .build());
    }

    /**
     * 仅在业务事务提交后调用。失败时保留 outbox 记录，由定时任务继续补发。
     */
    public void sendNow(String destination, String body) {
        String messageKey = messageKey(destination, body);
        try {
            rocketMQTemplate.syncSend(destination, MessageBuilder.withPayload(body).build());
            mqSendFailureMapper.deleteByMessageKey(messageKey);
        } catch (Exception e) {
            log.warn("MQ 事件即时发送失败，已保留在 outbox 中，destination={}", destination, e);
        }
    }

    private String messageKey(String destination, String body) {
        return DigestUtil.sha256Hex(destination + ':' + body);
    }
}
