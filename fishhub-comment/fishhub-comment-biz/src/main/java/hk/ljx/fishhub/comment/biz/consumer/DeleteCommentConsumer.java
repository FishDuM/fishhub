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
import hk.ljx.fishhub.comment.biz.model.dto.DeleteCommentContentMqDTO;
import hk.ljx.fishhub.comment.biz.model.dto.InvalidateOneLevelCommentCacheMqDTO;
import hk.ljx.fishhub.comment.biz.retry.SendMqRetryHelper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.RedisTemplate;
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
    private SendMqRetryHelper sendMqRetryHelper;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private CommentDetailCache commentDetailCache;
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

        List<DeleteCommentContentMqDTO> contentDeletionTasks = targets.stream()
                .filter(target -> !Boolean.TRUE.equals(target.getIsContentEmpty())
                        && StringUtils.isNotBlank(target.getContentUuid()))
                .map(target -> DeleteCommentContentMqDTO.builder()
                        .noteId(target.getNoteId())
                        .createTime(target.getCreateTime())
                        .contentUuid(target.getContentUuid())
                        .build())
                .toList();
        List<String> contentDeletionBodies = contentDeletionTasks.stream()
                .map(JsonUtils::toJsonString)
                .toList();
        String heatUpdateBody = Objects.equals(root.getLevel(), CommentLevelEnum.TWO.getCode())
                ? JsonUtils.toJsonString(Set.of(root.getParentId())) : null;
        List<String> localCacheInvalidationBodies = new ArrayList<>(targetIds.stream()
                .map(String::valueOf)
                .toList());
        if (Objects.equals(root.getLevel(), CommentLevelEnum.TWO.getCode())) {
            localCacheInvalidationBodies.add(String.valueOf(root.getParentId()));
        }
        String oneLevelCacheInvalidationBody = Objects.equals(root.getLevel(), CommentLevelEnum.ONE.getCode())
                ? JsonUtils.toJsonString(InvalidateOneLevelCommentCacheMqDTO.builder()
                        .eventId(java.util.UUID.randomUUID().toString())
                        .noteId(root.getNoteId())
                        .build()) : null;

        boolean deleted = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
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

            contentDeletionBodies.forEach(bodyItem ->
                    sendMqRetryHelper.enqueue(MQConstants.TOPIC_DELETE_COMMENT_CONTENT, bodyItem));
            if (heatUpdateBody != null) {
                sendMqRetryHelper.enqueue(MQConstants.TOPIC_COMMENT_HEAT_UPDATE, heatUpdateBody);
            }
            localCacheInvalidationBodies.forEach(bodyItem ->
                    sendMqRetryHelper.enqueue(MQConstants.TOPIC_DELETE_COMMENT_LOCAL_CACHE, bodyItem));
            if (oneLevelCacheInvalidationBody != null) {
                sendMqRetryHelper.enqueue(MQConstants.TOPIC_INVALIDATE_ONE_LEVEL_COMMENT_CACHE,
                        oneLevelCacheInvalidationBody);
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
            }
            return true;
        }));

        if (!deleted) {
            return;
        }

        invalidateRedis(root, targetIds);
        contentDeletionBodies.forEach(bodyItem ->
                sendMqRetryHelper.sendNow(MQConstants.TOPIC_DELETE_COMMENT_CONTENT, bodyItem));
        if (heatUpdateBody != null) {
            sendMqRetryHelper.sendNow(MQConstants.TOPIC_COMMENT_HEAT_UPDATE, heatUpdateBody);
        }
        localCacheInvalidationBodies.forEach(bodyItem ->
                sendMqRetryHelper.sendNow(MQConstants.TOPIC_DELETE_COMMENT_LOCAL_CACHE, bodyItem));
        if (oneLevelCacheInvalidationBody != null) {
            sendMqRetryHelper.sendNow(MQConstants.TOPIC_INVALIDATE_ONE_LEVEL_COMMENT_CACHE,
                    oneLevelCacheInvalidationBody);
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

    private void invalidateRedis(CommentDO root, List<Long> targetIds) {
        if (Objects.equals(root.getLevel(), CommentLevelEnum.ONE.getCode())) {
            redisTemplate.delete(RedisKeyConstants.buildChildCommentListKey(root.getId()));
        } else {
            redisTemplate.opsForZSet().remove(
                    RedisKeyConstants.buildChildCommentListKey(root.getParentId()), targetIds.toArray());
            redisTemplate.delete(RedisKeyConstants.buildCountCommentKey(root.getParentId()));
            redisTemplate.delete(RedisKeyConstants.buildHaveFirstReplyCommentKey(root.getParentId()));
        }
        List<String> keys = new ArrayList<>();
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
