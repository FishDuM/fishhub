package hk.ljx.fishhub.user.relation.biz.retry;

import hk.ljx.fishhub.user.relation.biz.domain.dataobject.MqSendFailureDO;
import hk.ljx.fishhub.user.relation.biz.domain.mapper.MqSendFailureMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class FailedMqRetryJob {

    private static final int BATCH_SIZE = 100;
    private static final int LOCK_TIMEOUT_MINUTES = 10;
    private static final long MAX_RETRY_DELAY_SECONDS = TimeUnit.HOURS.toSeconds(1);

    @Resource
    private MqSendFailureMapper mqSendFailureMapper;
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Scheduled(fixedDelayString = "${mq.failed-message.retry-interval-ms:30000}",
            initialDelayString = "${mq.failed-message.initial-delay-ms:30000}")
    public void retryFailedMessages() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lockExpiredAt = now.minusMinutes(LOCK_TIMEOUT_MINUTES);
        List<MqSendFailureDO> failures = mqSendFailureMapper.selectRetryable(now, lockExpiredAt, BATCH_SIZE);

        for (MqSendFailureDO failure : failures) {
            if (mqSendFailureMapper.claim(failure.getId(), LocalDateTime.now(), lockExpiredAt) != 1) {
                continue;
            }
            try {
                rocketMQTemplate.syncSend(failure.getTopic(), MessageBuilder.withPayload(failure.getBody()).build());
                mqSendFailureMapper.deleteById(failure.getId());
            } catch (Exception e) {
                int retryCount = failure.getRetryCount() + 1;
                long delaySeconds = Math.min(1L << Math.min(retryCount, 12), MAX_RETRY_DELAY_SECONDS);
                mqSendFailureMapper.releaseForRetry(
                        failure.getId(),
                        retryCount,
                        LocalDateTime.now().plusSeconds(delaySeconds),
                        StringUtils.abbreviate(e.getMessage() == null ? e.getClass().getName() : e.getMessage(), 1000));
                log.warn("outbox 消息补发失败，id={}, retryCount={}", failure.getId(), retryCount, e);
            }
        }
    }
}
