package hk.ljx.fishhub.count.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Lists;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.count.biz.constant.MQConstants;
import hk.ljx.fishhub.count.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.count.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.count.biz.enums.CommentLevelEnum;
import hk.ljx.fishhub.count.biz.model.dto.CountPublishCommentMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_child_comment_total" + MQConstants.TOPIC_COUNT_NOTE_COMMENT, // Group 组
        topic = MQConstants.TOPIC_COUNT_NOTE_COMMENT // 主题 Topic
        )
@Slf4j
public class CountNoteChildCommentConsumer implements RocketMQListener<String> {

    @Resource
    private CommentDOMapper commentDOMapper;
    @Resource
    private RocketMQTemplate rocketMQTemplate;
    @Resource
    private hk.ljx.fishhub.count.biz.service.MqIdempotentExecutor mqIdempotentExecutor;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onMessage(String body) {
        // 完成处理后才由 RocketMQ 确认消息，避免仅入内存队列即 ACK。
        consumeMessage(List.of(body));
    }

    private void consumeMessage(List<String> bodys) {
        log.info("==> 【笔记二级评论数】聚合消息, size: {}", bodys.size());
        log.info("==> 【笔记二级评论数】聚合消息, {}", JsonUtils.toJsonString(bodys));

        // 将聚合后的消息体 Json 转 List<CountPublishCommentMqDTO>
        List<CountPublishCommentMqDTO> countPublishCommentMqDTOList = Lists.newArrayList();
        bodys.forEach(body -> {
            try {
                List<CountPublishCommentMqDTO> list = JsonUtils.parseList(body, CountPublishCommentMqDTO.class);
                if (CollUtil.isEmpty(list) || list.stream().anyMatch(item -> item.getNoteId() == null
                        || item.getCommentId() == null || item.getLevel() == null
                        || (Objects.equals(item.getLevel(), CommentLevelEnum.TWO.getCode())
                        && item.getParentId() == null))) {
                    throw new IllegalArgumentException("二级评论计数消息缺少必要字段");
                }
                countPublishCommentMqDTOList.addAll(list);
            } catch (Exception e) {
                throw new IllegalArgumentException("二级评论计数消息格式错误", e);
            }
        });

        // 过滤出二级评论，并按 parent_id 分组
        Map<Long, List<CountPublishCommentMqDTO>> groupMap = countPublishCommentMqDTOList.stream()
                .filter(commentMqDTO -> Objects.equals(CommentLevelEnum.TWO.getCode(), commentMqDTO.getLevel()))
                .collect(Collectors.groupingBy(CountPublishCommentMqDTO::getParentId)); // 按 parent_id 分组

        // 若无二级评论，则直接 return
        if (CollUtil.isEmpty(groupMap)) return;

        String batchId = cn.hutool.crypto.digest.DigestUtil.sha256Hex(String.join("|", bodys));
        mqIdempotentExecutor.execute("count-child-comment", batchId, () ->
                groupMap.forEach((parentId, comments) ->
                        commentDOMapper.updateChildCommentTotal(parentId, CollUtil.size(comments))));

        // 获取字典中所有评论 ID
        Set<Long> commentIds = groupMap.keySet();
        redisTemplate.delete(commentIds.stream()
                .map(RedisKeyConstants::buildCountCommentKey)
                .toList());

        // 异步发送计数 MQ, 更新评论热度值
        org.springframework.messaging.Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(commentIds))
                .build();

        // 热度消费者按数据库最新值重算，重复投递安全；同步发送失败时让源消息重试。
        rocketMQTemplate.syncSend(MQConstants.TOPIC_COMMENT_HEAT_UPDATE, message);
    }
}
