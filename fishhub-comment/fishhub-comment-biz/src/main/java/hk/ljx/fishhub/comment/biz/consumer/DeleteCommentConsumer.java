package hk.ljx.fishhub.comment.biz.consumer;

import cn.hutool.crypto.digest.DigestUtil;
import com.google.common.util.concurrent.RateLimiter;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentLikeDOMapper;
import hk.ljx.fishhub.comment.biz.domain.mapper.MqConsumeRecordMapper;
import hk.ljx.fishhub.comment.biz.domain.mapper.NoteCountDOMapper;
import hk.ljx.fishhub.comment.biz.enums.CommentLevelEnum;
import hk.ljx.fishhub.comment.biz.model.dto.CommentChangedEventMqDTO;
import hk.ljx.fishhub.comment.biz.model.dto.CommentItemMqDTO;
import hk.ljx.framework.mq.tx.TransactionalMqSender;
import hk.ljx.framework.mq.tx.TxJournalStore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;

@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_DELETE_COMMENT,
        topic = MQConstants.TOPIC_DELETE_COMMENT)
public class DeleteCommentConsumer implements RocketMQListener<String> {

    @Resource
    private CommentDOMapper commentDOMapper;
    @Resource
    private CommentLikeDOMapper commentLikeDOMapper;
    @Resource
    private NoteCountDOMapper noteCountDOMapper;
    @Resource
    private MqConsumeRecordMapper mqConsumeRecordMapper;
    @Resource
    private TransactionalMqSender transactionalMqSender;
    @Resource
    private TxJournalStore txJournalStore;
    @Resource
    private TransactionTemplate transactionTemplate;

    private final RateLimiter rateLimiter = RateLimiter.create(1000);

    @Override
    public void onMessage(String body) {
        rateLimiter.acquire();
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
        String eventBody = JsonUtils.toJsonString(CommentChangedEventMqDTO.builder()
                .changeType(MQConstants.COMMENT_CHANGE_TYPE_DELETE)
                .items(eventItems)
                .build());

        transactionalMqSender.sendInTransaction(MQConstants.TOPIC_COMMENT_CHANGED, eventBody, txId -> {
            boolean deleted = transactionTemplate.execute(status -> {
                // 消费幂等：并发重投时只允许一个消费者执行扣减；未生效则不登记 journal，半消息随之回滚
                String messageKey = DigestUtil.sha256Hex(MQConstants.TOPIC_DELETE_COMMENT + ":" + body);
                if (mqConsumeRecordMapper.exists(
                        "fishhub_group_" + MQConstants.TOPIC_DELETE_COMMENT, messageKey) > 0) {
                    return false;
                }
                try {
                    mqConsumeRecordMapper.insert(
                            "fishhub_group_" + MQConstants.TOPIC_DELETE_COMMENT, messageKey);
                } catch (DuplicateKeyException e) {
                    return false;
                }

                commentLikeDOMapper.deleteByCommentIds(targetIds);
                commentDOMapper.deleteByIds(targetIds);
                noteCountDOMapper.insertOrUpdateCommentTotalByNoteId(root.getNoteId(), -targetIds.size());

                if (Objects.equals(root.getLevel(), CommentLevelEnum.TWO.getCode())) {
                    Long parentId = root.getParentId();
                    commentDOMapper.updateChildCommentTotal(parentId, -targetIds.size());
                    CommentDO earliest = commentDOMapper.selectEarliestByParentId(parentId);
                    commentDOMapper.updateFirstReplyCommentIdByPrimaryKey(
                            earliest == null ? 0L : earliest.getId(), parentId);
                }
                txJournalStore.record(txId);
                return true;
            });
            log.info("评论删除事务完成, rootId={}, applied={}", root.getId(), deleted);
            return deleted;
        });
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
