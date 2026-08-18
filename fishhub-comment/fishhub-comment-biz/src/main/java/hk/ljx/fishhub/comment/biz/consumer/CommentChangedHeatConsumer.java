package hk.ljx.fishhub.comment.biz.consumer;

import com.google.common.collect.Sets;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.enums.CommentLevelEnum;
import hk.ljx.fishhub.count.dto.CommentChangedEventMqDTO;
import hk.ljx.fishhub.count.dto.CommentItemMqDTO;
import hk.ljx.fishhub.comment.biz.service.CommentHeatAggregator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

/**
 * 消费评论变更事件，提取受影响的一级评论 ID 交给聚合器合并重算热度。
 */
@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_COMMENT_CHANGED + "_heat",
        topic = MQConstants.TOPIC_COMMENT_CHANGED)
@Slf4j
public class CommentChangedHeatConsumer implements RocketMQListener<String> {

    @Resource
    private CommentHeatAggregator commentHeatAggregator;

    @Override
    public void onMessage(String body) {
        CommentChangedEventMqDTO event = JsonUtils.parseObject(body, CommentChangedEventMqDTO.class);
        if (event == null || event.getChangeType() == null || event.getItems() == null) {
            throw new IllegalArgumentException("评论热度消息格式错误");
        }

        // 二级评论的变动会影响其父评论的热度
        Set<Long> commentIds = Sets.newHashSet();
        event.getItems().stream()
                .filter(item -> Objects.equals(item.getLevel(), CommentLevelEnum.TWO.getCode()))
                .map(CommentItemMqDTO::getParentId)
                .filter(Objects::nonNull)
                .forEach(commentIds::add);
        commentHeatAggregator.submit(commentIds);
    }
}
