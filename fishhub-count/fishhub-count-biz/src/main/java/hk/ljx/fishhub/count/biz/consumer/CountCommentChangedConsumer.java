package hk.ljx.fishhub.count.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.count.biz.constant.MQConstants;
import hk.ljx.fishhub.count.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.count.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.count.biz.domain.mapper.NoteCountDOMapper;
import hk.ljx.fishhub.count.biz.enums.CommentLevelEnum;
import hk.ljx.fishhub.count.biz.model.dto.CommentChangedEventMqDTO;
import hk.ljx.fishhub.count.biz.model.dto.CommentItemMqDTO;
import hk.ljx.fishhub.count.biz.service.MqIdempotentExecutor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 消费评论变更统一事件，维护评论相关计数：
 * 发布 —— 笔记评论总数（按笔记聚合）与一级评论的二级评论总数（按父评论聚合）；
 * 删除 —— 计数扣减已在评论模块的删除事务内直接完成，本消费者只负责失效对应缓存。
 */
@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_COMMENT_CHANGED, // Group 组
        topic = MQConstants.TOPIC_COMMENT_CHANGED // 主题 Topic
        )
@Slf4j
public class CountCommentChangedConsumer implements RocketMQListener<String> {

    @Resource
    private NoteCountDOMapper noteCountDOMapper;
    @Resource
    private CommentDOMapper commentDOMapper;
    @Resource
    private MqIdempotentExecutor mqIdempotentExecutor;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void onMessage(String body) {
        log.info("==> CountCommentChangedConsumer 消费了消息 {}", body);

        CommentChangedEventMqDTO event = JsonUtils.parseObject(body, CommentChangedEventMqDTO.class);
        if (event == null || event.getChangeType() == null || CollUtil.isEmpty(event.getItems())
                || event.getItems().stream().anyMatch(item -> item.getNoteId() == null
                || item.getId() == null || item.getLevel() == null
                || (Objects.equals(item.getLevel(), CommentLevelEnum.TWO.getCode()) && item.getParentId() == null))) {
            throw new IllegalArgumentException("评论计数消息缺少必要字段");
        }

        boolean isPublish = Objects.equals(event.getChangeType(), MQConstants.COMMENT_CHANGE_TYPE_PUBLISH);
        if (isPublish) {
            applyPublishCounts(event, body);
        }
        invalidateCountCaches(event, isPublish);
    }

    private void applyPublishCounts(CommentChangedEventMqDTO event, String body) {
        // 按笔记聚合评论总数
        Map<Long, List<CommentItemMqDTO>> groupByNoteId = event.getItems().stream()
                .collect(Collectors.groupingBy(CommentItemMqDTO::getNoteId));

        mqIdempotentExecutor.execute("count-note-comment", body, () ->
                groupByNoteId.forEach((noteId, comments) ->
                        noteCountDOMapper.insertOrUpdateCommentTotalByNoteId(comments.size(), noteId)));

        // 二级评论按 parent_id 聚合，更新一级评论的 child_comment_total
        Map<Long, List<CommentItemMqDTO>> groupByParentId = event.getItems().stream()
                .filter(item -> Objects.equals(item.getLevel(), CommentLevelEnum.TWO.getCode()))
                .collect(Collectors.groupingBy(CommentItemMqDTO::getParentId));

        if (CollUtil.isNotEmpty(groupByParentId)) {
            mqIdempotentExecutor.execute("count-child-comment", body, () ->
                    groupByParentId.forEach((parentId, comments) ->
                            commentDOMapper.updateChildCommentTotal(parentId, comments.size())));
        }
    }

    private void invalidateCountCaches(CommentChangedEventMqDTO event, boolean isPublish) {
        List<String> noteCountKeys = event.getItems().stream()
                .map(CommentItemMqDTO::getNoteId)
                .distinct()
                .map(RedisKeyConstants::buildCountNoteKey)
                .collect(Collectors.toList());

        // 删除流程的笔记评论总数扣减在评论模块事务内完成，此处只需清缓存
        stringRedisTemplate.delete(noteCountKeys);
        if (!isPublish) {
            List<String> commentCountKeys = event.getItems().stream()
                    .filter(item -> Objects.equals(item.getLevel(), CommentLevelEnum.TWO.getCode()))
                    .map(CommentItemMqDTO::getParentId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .map(RedisKeyConstants::buildCountCommentKey)
                    .collect(Collectors.toList());
            stringRedisTemplate.delete(commentCountKeys);
        }
    }
}
