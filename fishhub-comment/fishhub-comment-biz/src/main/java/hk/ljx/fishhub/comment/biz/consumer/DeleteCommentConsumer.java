package hk.ljx.fishhub.comment.biz.consumer;

import cn.hutool.crypto.digest.DigestUtil;
import com.google.common.util.concurrent.RateLimiter;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.cache.CommentDetailCache;
import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentLikeDOMapper;
import hk.ljx.fishhub.comment.biz.domain.mapper.MqConsumeRecordMapper;
import hk.ljx.fishhub.comment.biz.domain.mapper.NoteCountDOMapper;
import hk.ljx.fishhub.comment.biz.enums.CommentLevelEnum;
import hk.ljx.fishhub.comment.biz.rpc.KeyValueRpcService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.support.MessageBuilder;
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
    private KeyValueRpcService keyValueRpcService;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private CommentDetailCache commentDetailCache;
    @Resource
    private RocketMQTemplate rocketMQTemplate;
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

        // 主评论已不存在说明上一次已整体提交，直接 ACK，避免重复扣减计数。
        CommentDO root = commentDOMapper.selectByPrimaryKey(payload.getId());
        if (root == null) {
            return;
        }

        List<CommentDO> targets = collectDeleteTargets(root);
        List<Long> targetIds = targets.stream().map(CommentDO::getId).distinct().toList();

        // Cassandra 删除在 MySQL 事务前执行，失败则不会删除元数据，由 RocketMQ 重试。
        for (CommentDO target : targets) {
            if (!Boolean.TRUE.equals(target.getIsContentEmpty())
                    && StringUtils.isNotBlank(target.getContentUuid())) {
                keyValueRpcService.deleteCommentContent(
                        target.getNoteId(), target.getCreateTime(), target.getContentUuid());
            }
        }

        invalidateRedis(root, targetIds);

        transactionTemplate.execute(status -> {
            // 消费幂等：并发重投时只允许一个消费者执行扣减
            String messageKey = DigestUtil.sha256Hex(MQConstants.TOPIC_DELETE_COMMENT + ":" + body);
            if (mqConsumeRecordMapper.exists(
                    "fishhub_group_" + MQConstants.TOPIC_DELETE_COMMENT, messageKey) > 0) {
                return null;
            }
            try {
                mqConsumeRecordMapper.insert(
                        "fishhub_group_" + MQConstants.TOPIC_DELETE_COMMENT, messageKey);
            } catch (DuplicateKeyException e) {
                return null;
            }

            commentLikeDOMapper.deleteByCommentIds(targetIds);
            commentDOMapper.deleteByIds(targetIds);
            noteCountDOMapper.updateCommentTotalByNoteId(root.getNoteId(), -targetIds.size());

            if (Objects.equals(root.getLevel(), CommentLevelEnum.TWO.getCode())) {
                Long parentId = root.getParentId();
                commentDOMapper.updateChildCommentTotal(parentId, -targetIds.size());
                CommentDO earliest = commentDOMapper.selectEarliestByParentId(parentId);
                commentDOMapper.updateFirstReplyCommentIdByPrimaryKey(
                        earliest == null ? 0L : earliest.getId(), parentId);
                rocketMQTemplate.syncSend(MQConstants.TOPIC_COMMENT_HEAT_UPDATE,
                        MessageBuilder.withPayload(JsonUtils.toJsonString(Set.of(parentId))).build());
            }

            // 广播删除每个实例的 Caffeine 详情缓存。
            targetIds.forEach(id -> rocketMQTemplate.syncSend(
                    MQConstants.TOPIC_DELETE_COMMENT_LOCAL_CACHE, String.valueOf(id)));
            if (Objects.equals(root.getLevel(), CommentLevelEnum.TWO.getCode())) {
                rocketMQTemplate.syncSend(MQConstants.TOPIC_DELETE_COMMENT_LOCAL_CACHE, String.valueOf(root.getParentId()));
            }
            return true;
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

    private void invalidateRedis(CommentDO root, List<Long> targetIds) {
        if (Objects.equals(root.getLevel(), CommentLevelEnum.ONE.getCode())) {
            redisTemplate.opsForZSet().remove(
                    RedisKeyConstants.buildCommentListKey(root.getNoteId()), root.getId());
            redisTemplate.delete(RedisKeyConstants.buildChildCommentListKey(root.getId()));
        } else {
            redisTemplate.opsForZSet().remove(
                    RedisKeyConstants.buildChildCommentListKey(root.getParentId()), targetIds.toArray());
            redisTemplate.delete(RedisKeyConstants.buildCountCommentKey(root.getParentId()));
        }
        List<String> keys = new ArrayList<>();
        keys.add(RedisKeyConstants.buildNoteCommentTotalKey(root.getNoteId()));
        List<String> detailKeys = new ArrayList<>();
        if (Objects.equals(root.getLevel(), CommentLevelEnum.TWO.getCode())) {
            detailKeys.add(RedisKeyConstants.buildCommentDetailKey(root.getParentId()));
        }
        targetIds.forEach(id -> {
            detailKeys.add(RedisKeyConstants.buildCommentDetailKey(id));
            keys.add(RedisKeyConstants.buildCountCommentKey(id));
        });
        redisTemplate.delete(keys);
        commentDetailCache.delete(detailKeys);
    }
}
