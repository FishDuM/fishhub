package hk.ljx.fishhub.count.biz.consumer;

import cn.hutool.crypto.digest.DigestUtil;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.count.biz.constant.MQConstants;
import hk.ljx.fishhub.count.biz.enums.FollowUnfollowTypeEnum;
import hk.ljx.fishhub.count.biz.model.dto.AggregationCountFansMqDTO;
import hk.ljx.fishhub.count.biz.model.dto.CountFollowUnfollowMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_COUNT_FANS,
        topic = MQConstants.TOPIC_COUNT_FANS)
@Slf4j
public class CountFansConsumer implements RocketMQListener<String> {

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void onMessage(String body) {
        CountFollowUnfollowMqDTO event = JsonUtils.parseObject(body, CountFollowUnfollowMqDTO.class);
        if (event == null || event.getTargetUserId() == null || event.getType() == null
                || event.getCreateTime() == null) {
            throw new IllegalArgumentException("粉丝计数消息缺少必要字段");
        }

        int count = Objects.equals(event.getType(), FollowUnfollowTypeEnum.FOLLOW.getCode()) ? 1 : -1;
        AggregationCountFansMqDTO aggregate = AggregationCountFansMqDTO.builder()
                .targetUserId(event.getTargetUserId())
                .count(count)
                .batchId(DigestUtil.sha256Hex(body))
                .build();

        rocketMQTemplate.syncSend(
                MQConstants.TOPIC_COUNT_FANS_2_DB,
                MessageBuilder.withPayload(JsonUtils.toJsonString(List.of(aggregate))).build());
    }
}
