package hk.ljx.fishhub.comment.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.RateLimiter;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.enums.LikeUnlikeCommentTypeEnum;
import hk.ljx.fishhub.comment.biz.model.dto.LikeUnlikeCommentMqDTO;
import hk.ljx.fishhub.comment.biz.rpc.NoteRpcService;
import hk.ljx.fishhub.comment.biz.service.CommentLikePersistenceService;
import hk.ljx.fishhub.note.api.NoteWriteAccessCheckReqDTO;
import hk.ljx.framework.mq.consumer.BatchConsumerFactory;
import hk.ljx.framework.mq.consumer.BatchPushConsumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * 评论点赞/取消点赞批量落库消费者
 */
@Component
@Slf4j
public class LikeUnlikeComment2DBConsumer {

    private static final int BATCH_MAX_SIZE = 30;
    private static final int MAX_RECONSUME_TIMES = 16;

    private final CommentLikePersistenceService persistenceService;
    private final CommentDOMapper commentDOMapper;
    private final NoteRpcService noteRpcService;
    private final RocketMQTemplate rocketMQTemplate;
    private final BatchPushConsumer batchPushConsumer;

    // 每秒创建 5000 个令牌
    private final RateLimiter rateLimiter = RateLimiter.create(5000);

    public LikeUnlikeComment2DBConsumer(CommentLikePersistenceService persistenceService,
                                        CommentDOMapper commentDOMapper,
                                        NoteRpcService noteRpcService,
                                        RocketMQTemplate rocketMQTemplate,
                                        BatchConsumerFactory batchConsumerFactory) throws MQClientException {
        this.persistenceService = persistenceService;
        this.commentDOMapper = commentDOMapper;
        this.noteRpcService = noteRpcService;
        this.rocketMQTemplate = rocketMQTemplate;
        this.batchPushConsumer = batchConsumerFactory == null ? null : batchConsumerFactory.create(
                "fishhub_group_" + MQConstants.TOPIC_COMMENT_LIKE_OR_UNLIKE,
                MQConstants.TOPIC_COMMENT_LIKE_OR_UNLIKE,
                "*",
                BATCH_MAX_SIZE,
                MAX_RECONSUME_TIMES,
                BatchConsumerFactory.Mode.ORDERLY,
                this::consumeBatch);
    }

    private boolean consumeBatch(List<MessageExt> msgs) {
        log.info("==> 【评论点赞、取消点赞】本批次消息大小: {}", msgs.size());
        try {
            // 令牌桶流控, 以控制数据库能够承受的 QPS
            rateLimiter.acquire();

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
                    // 反序列化失败无法通过重试恢复，确认该消息，避免阻塞同一顺序队列。
                    log.error("丢弃无法解析的评论点赞消息, msgId={}, payloadSize={}",
                            msg.getMsgId(), msgJson.length(), e);
                }
            });

            if (CollUtil.isEmpty(likeUnlikeCommentMqDTOS)) {
                return true;
            }

            // 按评论 ID 分组
            Map<Long, List<LikeUnlikeCommentMqDTO>> commentIdAndListMap = likeUnlikeCommentMqDTOS.stream()
                    .collect(Collectors.groupingBy(LikeUnlikeCommentMqDTO::getCommentId));

            List<LikeUnlikeCommentMqDTO> finalLikeUnlikeCommentMqDTOS = Lists.newArrayList();

            commentIdAndListMap.forEach((commentId, ops) -> {
                // 合并同一用户的多次操作，保留最新一条
                Map<Long, LikeUnlikeCommentMqDTO> userLastOp = ops.stream()
                        .collect(Collectors.toMap(
                                LikeUnlikeCommentMqDTO::getUserId,
                                Function.identity(),
                                (oldValue, newValue) ->
                                        oldValue.getCreateTime().isAfter(newValue.getCreateTime()) ? oldValue : newValue
                        ));

                finalLikeUnlikeCommentMqDTOS.addAll(userLastOp.values());
            });

            Map<Long, CommentDO> comments = commentDOMapper.selectNoteIdsByCommentIds(
                            finalLikeUnlikeCommentMqDTOS.stream()
                                    .map(LikeUnlikeCommentMqDTO::getCommentId)
                                    .distinct()
                                    .toList())
                    .stream()
                    .collect(Collectors.toMap(CommentDO::getId, Function.identity()));
            List<NoteWriteAccessCheckReqDTO> writeChecks = finalLikeUnlikeCommentMqDTOS.stream()
                    .filter(operation -> Objects.equals(operation.getType(), LikeUnlikeCommentTypeEnum.LIKE.getCode()))
                    .map(operation -> {
                        CommentDO comment = comments.get(operation.getCommentId());
                        return comment == null ? null : NoteWriteAccessCheckReqDTO.builder()
                                .noteId(comment.getNoteId())
                                .userId(operation.getUserId())
                                .build();
                    })
                    .filter(Objects::nonNull)
                    .toList();
            Set<NoteWriteAccessCheckReqDTO> writableAccesses = new HashSet<>(
                    noteRpcService.findWritableNoteAccesses(writeChecks));

            Set<Long> appliedCommentIds = new LinkedHashSet<>();
            List<LikeUnlikeCommentMqDTO> persistOps = Lists.newArrayList();
            for (LikeUnlikeCommentMqDTO operation : finalLikeUnlikeCommentMqDTOS) {
                CommentDO comment = comments.get(operation.getCommentId());
                if (comment == null) {
                    log.info("点赞/取消点赞落库时评论不存在或已被删除，丢弃消息, commentId={}, userId={}, type={}",
                            operation.getCommentId(), operation.getUserId(), operation.getType());
                    continue;
                }
                if (Objects.equals(operation.getType(), LikeUnlikeCommentTypeEnum.LIKE.getCode())
                        && !writableAccesses.contains(NoteWriteAccessCheckReqDTO.builder()
                        .noteId(comment.getNoteId())
                        .userId(operation.getUserId())
                        .build())) {
                    log.info("丢弃不可写笔记上的评论点赞，commentId={}, userId={}",
                            operation.getCommentId(), operation.getUserId());
                    continue;
                }
                persistOps.add(operation);
            }

            // 批量落库
            if (CollUtil.isNotEmpty(persistOps)) {
                appliedCommentIds.addAll(persistenceService.applyBatch(persistOps));
            }

            // 触发热度更新
            if (CollUtil.isNotEmpty(appliedCommentIds)) {
                Message<String> heatMessage = MessageBuilder.withPayload(JsonUtils.toJsonString(appliedCommentIds)).build();
                rocketMQTemplate.syncSend(MQConstants.TOPIC_COMMENT_HEAT_UPDATE, heatMessage);
            }

            return true;
        } catch (Exception e) {
            log.error("评论点赞批量消费异常", e);
            return false;
        }
    }

}
