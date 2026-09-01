package hk.ljx.fishhub.comment.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Lists;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.enums.CommentLevelEnum;
import hk.ljx.fishhub.comment.biz.enums.LikeUnlikeCommentTypeEnum;
import hk.ljx.fishhub.comment.biz.model.dto.LikeUnlikeCommentMqDTO;
import hk.ljx.fishhub.comment.biz.service.CommentLikePersistenceService;
import hk.ljx.fishhub.comment.biz.service.CommentLikeRealtimeService;
import hk.ljx.framework.mq.support.RocketMqHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 评论点赞/取消点赞微批落库消费者
 */
@Component
@Slf4j
@RocketMQMessageListener(
        consumerGroup = "fishhub_group_" + MQConstants.TOPIC_COMMENT_LIKE_OR_UNLIKE,
        topic = MQConstants.TOPIC_COMMENT_LIKE_OR_UNLIKE,
        consumeMode = ConsumeMode.ORDERLY
)
public class LikeUnlikeComment2DBConsumer implements RocketMQListener<MessageExt> {

    private static final int BATCH_MAX_SIZE = 30;
    private static final int MAX_RECONSUME_TIMES = 16;

    private final CommentLikePersistenceService persistenceService;
    private final CommentDOMapper commentDOMapper;
    private final CommentLikeRealtimeService commentLikeRealtimeService;
    private final RocketMQTemplate rocketMQTemplate;

    public LikeUnlikeComment2DBConsumer(CommentLikePersistenceService persistenceService,
                                        CommentDOMapper commentDOMapper,
                                        CommentLikeRealtimeService commentLikeRealtimeService,
                                        RocketMQTemplate rocketMQTemplate) {
        this.persistenceService = persistenceService;
        this.commentDOMapper = commentDOMapper;
        this.commentLikeRealtimeService = commentLikeRealtimeService;
        this.rocketMQTemplate = rocketMQTemplate;
    }

    @Override
    public void onMessage(MessageExt msg) {
        if (msg == null) {
            return;
        }
        boolean success = consumeBatch(List.of(msg));
        if (!success) {
            throw new RuntimeException("评论点赞微批消费失败，触发 RocketMQ 顺序重试");
        }
    }

    private boolean consumeBatch(List<MessageExt> msgs) {
        log.info("==> 【评论点赞、取消点赞】本批次消息大小: {}", msgs.size());
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                processBatch(msgs);
                return true;
            } catch (org.springframework.dao.ConcurrencyFailureException e) {
                if (attempt == maxRetries) {
                    log.error("评论点赞批量消费死锁重试 {} 次仍失败，稍后由 MQ 重投", maxRetries, e);
                    return false;
                }
                long backoff = 15L + (long) (Math.random() * 35);
                log.warn("评论点赞批量入库遭遇 MySQL 并发争锁，执行第 {} 次本地退避重试 (backoff={}ms)", attempt, backoff);
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            } catch (Exception e) {
                log.error("评论点赞批量消费异常", e);
                return false;
            }
        }
        return false;
    }

    private void processBatch(List<MessageExt> msgs) {
        // 将批次 Json 消息体转换 DTO 集合
        List<LikeUnlikeCommentMqDTO> likeUnlikeCommentMqDTOS = Lists.newArrayList();

        msgs.forEach(msg -> {
            String tag = msg.getTags(); // Tag 标签
            String msgJson = new String(msg.getBody(), StandardCharsets.UTF_8); // 消息体 Json 字符串
            log.info("处理评论点赞事件，tag={}, payloadSize={}", tag, msgJson.length());

            try {
                LikeUnlikeCommentMqDTO operation = JsonUtils.parseObject(msgJson, LikeUnlikeCommentMqDTO.class);
                if (operation == null || operation.getCommentId() == null || operation.getUserId() == null
                        || operation.getType() == null || operation.getCreateTime() == null) {
                    log.error("丢弃缺少业务主键的评论点赞消息, msgId={}, payloadSize={}",
                            msg.getMsgId(), msgJson.length());
                    return;
                }
                likeUnlikeCommentMqDTOS.add(operation);
            } catch (Exception e) {
                log.error("丢弃无法解析的评论点赞消息, msgId={}, payloadSize={}",
                        msg.getMsgId(), msgJson.length(), e);
            }
        });

        // 按 (commentId, userId) 聚合，保留最新状态
        Map<String, LikeUnlikeCommentMqDTO> latestOperations = new LinkedHashMap<>();
        for (LikeUnlikeCommentMqDTO operation : likeUnlikeCommentMqDTOS) {
            String key = operation.getCommentId() + ":" + operation.getUserId();
            latestOperations.put(key, operation);
        }

        List<LikeUnlikeCommentMqDTO> mergedOperations = new ArrayList<>(latestOperations.values());
        if (CollUtil.isEmpty(mergedOperations)) {
            return;
        }

        // 仅处理数据库中已存在的评论
        List<Long> commentIds = mergedOperations.stream()
                .map(LikeUnlikeCommentMqDTO::getCommentId)
                .distinct()
                .toList();
        List<CommentDO> commentDOS = commentDOMapper.selectNoteIdsByCommentIds(commentIds);
        Map<Long, CommentDO> comments = CollUtil.isEmpty(commentDOS) ? Collections.emptyMap() :
                commentDOS.stream().collect(Collectors.toMap(CommentDO::getId, Function.identity()));

        List<Long> appliedCommentIds = new ArrayList<>();
        List<LikeUnlikeCommentMqDTO> persistOps = new ArrayList<>();
        for (LikeUnlikeCommentMqDTO operation : mergedOperations) {
            if (!comments.containsKey(operation.getCommentId())) {
                if (Objects.equals(operation.getType(), LikeUnlikeCommentTypeEnum.UNLIKE.getCode())) {
                    // 评论已在 t_comment 中删除，仍放行执行 t_comment_like 的物理删除以清理残留关系行
                    persistOps.add(operation);
                } else {
                    log.warn("丢弃不可写/已删除评论上的点赞并回滚实时缓存，commentId={}, userId={}",
                            operation.getCommentId(), operation.getUserId());
                    if (commentLikeRealtimeService != null) {
                        commentLikeRealtimeService.markUnliked(operation.getUserId(), operation.getCommentId());
                    }
                }
                continue;
            }
            persistOps.add(operation);
        }

        // 批量落库
        if (CollUtil.isNotEmpty(persistOps)) {
            appliedCommentIds.addAll(persistenceService.applyBatch(persistOps));
        }

        // 异步触发热度更新（仅对仍存在的一级/有效评论发送，避免对已删评论空转重算）
        if (CollUtil.isNotEmpty(appliedCommentIds)) {
            Set<Long> validAppliedCommentIds = appliedCommentIds.stream()
                    .filter(commentId -> {
                        CommentDO comment = comments.get(commentId);
                        return comment != null && Objects.equals(comment.getLevel(), CommentLevelEnum.ONE.getCode());
                    })
                    .collect(Collectors.toSet());
            if (CollUtil.isNotEmpty(validAppliedCommentIds)) {
                Message<String> heatMessage = MessageBuilder.withPayload(JsonUtils.toJsonString(validAppliedCommentIds)).build();
                RocketMqHelper.asyncSend(rocketMQTemplate, MQConstants.TOPIC_COMMENT_HEAT_UPDATE, heatMessage, "评论热度更新");
            }
        }
    }

}
