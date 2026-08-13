package hk.ljx.fishhub.note.biz.retry;

import hk.ljx.fishhub.note.biz.domain.dataobject.MqSendFailureDO;
import hk.ljx.fishhub.note.biz.domain.mapper.MqSendFailureMapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class FailedMqRetryJob {
    @Resource private MqSendFailureMapper mapper;
    @Resource private RocketMQTemplate rocketMQTemplate;

    @Scheduled(fixedDelayString = "${mq.failed-message.retry-interval-ms:30000}",
            initialDelayString = "${mq.failed-message.initial-delay-ms:30000}")
    public void retry() {
        LocalDateTime now = LocalDateTime.now();
        List<MqSendFailureDO> failures = mapper.selectRetryable(now, now.minusMinutes(10), 100);
        for (MqSendFailureDO failure : failures) {
            if (mapper.claim(failure.getId(), LocalDateTime.now(), now.minusMinutes(10)) != 1) continue;
            try {
                rocketMQTemplate.syncSend(failure.getTopic(), MessageBuilder.withPayload(failure.getBody()).build());
                mapper.deleteById(failure.getId());
            } catch (Exception e) {
                int retryCount = failure.getRetryCount() + 1;
                long delay = Math.min(1L << Math.min(retryCount, 12), 3600L);
                mapper.releaseForRetry(failure.getId(), retryCount, LocalDateTime.now().plusSeconds(delay),
                        StringUtils.abbreviate(e.getMessage() == null ? e.getClass().getName() : e.getMessage(), 1000));
            }
        }
    }
}
