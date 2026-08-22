package hk.ljx.fishhub.count.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.count.biz.constant.MQConstants;
import hk.ljx.fishhub.count.constant.CountKeyConstants;
import hk.ljx.fishhub.count.biz.domain.mapper.NoteCountDOMapper;
import hk.ljx.fishhub.count.biz.enums.CommentLevelEnum;
import hk.ljx.fishhub.count.dto.CommentChangedEventMqDTO;
import hk.ljx.fishhub.count.dto.CommentItemMqDTO;
import hk.ljx.framework.mq.idempotent.MqIdempotentExecutor;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
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
 * 评论变更计数消费者
 */
@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_COMMENT_CHANGED, // Group 组
        topic = MQConstants.TOPIC_COMMENT_CHANGED // 主题 Topic
        )
@Slf4j
@RequiredArgsConstructor
public class CountCommentChangedConsumer implements RocketMQListener<String> {

    private final NoteCountDOMapper noteCountDOMapper;
    private final MqIdempotentExecutor mqIdempotentExecutor;
    private final StringRedisTemplate stringRedisTemplate;

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
        } else {
            applyDeleteCounts(event, body);
        }
        invalidateCountCaches(event, isPublish);
    }

    private void applyPublishCounts(CommentChangedEventMqDTO event, String body) {
        // 按笔记聚合一级评论总数（comment_total 领域语义 = 一级评论数，二级评论数由 child_comment_total 承载）
        Map<Long, List<CommentItemMqDTO>> groupByNoteId = event.getItems().stream()
                .filter(item -> Objects.equals(item.getLevel(), CommentLevelEnum.ONE.getCode()))
                .collect(Collectors.groupingBy(CommentItemMqDTO::getNoteId));

        mqIdempotentExecutor.execute("count-note-comment", body, () ->
                groupByNoteId.forEach((noteId, comments) ->
                        noteCountDOMapper.insertOrUpdateCommentTotalByNoteId(comments.size(), noteId)));

    }

    /**
     * 删除事件：按笔记聚合取负，扣减 t_note_count（幂等）。
     * 子评论总数（child_comment_total）与首条回复由评论模块在自己的删除事务内维护。
     */
    private void applyDeleteCounts(CommentChangedEventMqDTO event, String body) {
        // 删除事件含级联子评论，只按一级评论数扣减，与发布侧口径一致
        Map<Long, List<CommentItemMqDTO>> groupByNoteId = event.getItems().stream()
                .filter(item -> Objects.equals(item.getLevel(), CommentLevelEnum.ONE.getCode()))
                .collect(Collectors.groupingBy(CommentItemMqDTO::getNoteId));

        mqIdempotentExecutor.execute("count-note-comment-delete", body, () ->
                groupByNoteId.forEach((noteId, comments) ->
                        noteCountDOMapper.insertOrUpdateCommentTotalByNoteId(-comments.size(), noteId)));
    }

    private void invalidateCountCaches(CommentChangedEventMqDTO event, boolean isPublish) {
        List<String> noteCountKeys = event.getItems().stream()
                .map(CommentItemMqDTO::getNoteId)
                .distinct()
                .map(CountKeyConstants::buildCountNoteKey)
                .collect(Collectors.toList());

        // 删除流程的笔记评论总数扣减由 applyDeleteCounts 完成，此处只清缓存
        stringRedisTemplate.delete(noteCountKeys);
        if (!isPublish) {
            List<String> commentCountKeys = event.getItems().stream()
                    .filter(item -> Objects.equals(item.getLevel(), CommentLevelEnum.TWO.getCode()))
                    .map(CommentItemMqDTO::getParentId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .map(CountKeyConstants::buildCountCommentKey)
                    .collect(Collectors.toList());
            stringRedisTemplate.delete(commentCountKeys);
        }
    }
}
