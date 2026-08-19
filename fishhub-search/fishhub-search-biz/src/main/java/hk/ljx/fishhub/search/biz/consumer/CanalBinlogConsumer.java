package hk.ljx.fishhub.search.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.search.biz.canal.model.CanalFlatMessageDTO;
import hk.ljx.fishhub.search.biz.canal.service.CanalSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * Canal Binlog 消息消费者
 */
@Component
@RocketMQMessageListener(
        consumerGroup = "${canal.mq.group:fishhub_group_search_canal}",
        topic = "${canal.mq.topic:fishhub_canal_topic}"
)
@Slf4j
@RequiredArgsConstructor
public class CanalBinlogConsumer implements RocketMQListener<String> {

    private final CanalSyncService canalSyncService;

    @Override
    public void onMessage(String message) {
        if (StringUtils.isBlank(message)) {
            return;
        }
        try {
            CanalFlatMessageDTO flatMessage = JsonUtils.parseObject(message, CanalFlatMessageDTO.class);
            if (flatMessage != null) {
                canalSyncService.processFlatMessage(flatMessage);
            }
        } catch (Exception e) {
            log.error("==> 消费 Canal Binlog 消息异常, message: {}", message, e);
            throw new RuntimeException("消费 Canal Binlog 消息失败", e);
        }
    }
}
