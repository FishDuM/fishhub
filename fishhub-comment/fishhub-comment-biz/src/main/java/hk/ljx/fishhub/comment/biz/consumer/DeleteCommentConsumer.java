package hk.ljx.fishhub.comment.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentLikeDOMapper;
import hk.ljx.fishhub.comment.biz.enums.CommentLevelEnum;
import hk.ljx.fishhub.count.dto.CommentChangedEventMqDTO;
import hk.ljx.fishhub.count.dto.CommentItemMqDTO;
import hk.ljx.framework.mq.idempotent.MqIdempotentExecutor;
import hk.ljx.framework.mq.tx.TransactionalMqSender;
import hk.ljx.framework.mq.tx.TxJournalStore;
import hk.ljx.fishhub.comment.biz.service.CommentChangedLocalHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.stream.Collectors;


@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_DELETE_COMMENT,
        topic = MQConstants.TOPIC_DELETE_COMMENT)
@RequiredArgsConstructor
public class DeleteCommentConsumer implements RocketMQListener<String> {

    private final CommentDOMapper commentDOMapper;
    private final CommentLikeDOMapper commentLikeDOMapper;
    private final TransactionalMqSender transactionalMqSender;
    private final TxJournalStore txJournalStore;
    private final MqIdempotentExecutor mqIdempotentExecutor;
    private final CommentChangedLocalHandler commentChangedLocalHandler;

    @Override
    public void onMessage(String body) {
        CommentDO payload = JsonUtils.parseObject(body, CommentDO.class);
        if (payload == null || payload.getId() == null) {
            throw new IllegalArgumentException("评论删除消息格式错误");
        }

        // 主评论已不存在说明上一次已整体提交，缓存失效事件已由 outbox 保证投递，直接 ACK。
        CommentDO root = commentDOMapper.selectByPrimaryKey(payload.getId());
        if (root == null) {
            return;
        }

        List<CommentDO> targets = collectDeleteTargets(root);
        List<Long> targetIds = targets.stream().map(CommentDO::getId).distinct().toList();

        // 变更事件与删除事实经由事务消息原子绑定；条目集合在事务外基于同一快照构建，保持确定性。
        List<CommentItemMqDTO> eventItems = targets.stream()
                .map(target -> CommentItemMqDTO.builder()
                        .id(target.getId())
                        .noteId(target.getNoteId())
                        .level(target.getLevel())
                        .parentId(target.getParentId())
                        .userId(target.getUserId())
                        .contentUuid(target.getContentUuid())
                        .isContentEmpty(target.getIsContentEmpty())
                        .createTime(target.getCreateTime())
                        .build())
                .toList();
        CommentChangedEventMqDTO changeEvent = CommentChangedEventMqDTO.builder()
                .changeType(MQConstants.COMMENT_CHANGE_TYPE_DELETE)
                .items(eventItems)
                .build();
        String eventBody = JsonUtils.toJsonString(changeEvent);

        transactionalMqSender.sendInTransaction(MQConstants.TOPIC_COMMENT_CHANGED, eventBody, txId -> {
            boolean deleted = mqIdempotentExecutor.execute(
                    "fishhub_group_" + MQConstants.TOPIC_DELETE_COMMENT,
                    MQConstants.TOPIC_DELETE_COMMENT + ":" + body,
                    () -> {
                        commentLikeDOMapper.deleteByCommentIds(targetIds);
                        commentDOMapper.deleteByIds(targetIds);

                        if (Objects.equals(root.getLevel(), CommentLevelEnum.TWO.getCode())) {
                            Long parentId = root.getParentId();
                            commentDOMapper.updateChildCommentTotal(parentId, -targetIds.size());
                            CommentDO earliest = commentDOMapper.selectEarliestByParentId(parentId);
                            commentDOMapper.updateFirstReplyCommentIdByPrimaryKey(
                                    earliest == null ? 0L : earliest.getId(), parentId);
                        }
                        txJournalStore.record(txId);
                    });
            log.info("评论删除事务完成, rootId={}, applied={}", root.getId(), deleted);
            return deleted;
        });

        // 事务提交后本节点同步维护列表缓存/热度（原两个广播消费者合并）
        try {
            commentChangedLocalHandler.handleDelete(changeEvent);
        } catch (Exception e) {
            log.warn("评论删除本地动作执行失败（缓存/热度），等待 TTL/重建自愈", e);
        }
    }

    private List<CommentDO> collectDeleteTargets(CommentDO root) {
        List<CommentDO> targets = new ArrayList<>();
        targets.add(root);
        if (Objects.equals(root.getLevel(), CommentLevelEnum.ONE.getCode())) {
            targets.addAll(commentDOMapper.selectByParentId(root.getId()));
            return targets;
        }

        List<CommentDO> siblings = commentDOMapper.selectByParentId(root.getParentId());
        Map<Long, List<CommentDO>> repliesByTarget = siblings.stream()
                .filter(comment -> comment.getReplyCommentId() != null)
                .collect(Collectors.groupingBy(CommentDO::getReplyCommentId));
        Queue<Long> queue = new ArrayDeque<>();
        queue.add(root.getId());
        while (!queue.isEmpty()) {
            for (CommentDO child : repliesByTarget.getOrDefault(queue.remove(), List.of())) {
                targets.add(child);
                queue.add(child.getId());
            }
        }
        return targets;
    }

}
