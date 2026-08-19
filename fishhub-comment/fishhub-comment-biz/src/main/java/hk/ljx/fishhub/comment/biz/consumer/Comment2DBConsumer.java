package hk.ljx.fishhub.comment.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.RateLimiter;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.enums.CommentLevelEnum;
import hk.ljx.fishhub.comment.biz.model.bo.CommentBO;
import hk.ljx.fishhub.count.dto.CommentChangedEventMqDTO;
import hk.ljx.fishhub.count.dto.CommentItemMqDTO;
import hk.ljx.fishhub.comment.biz.model.dto.PublishCommentMqDTO;
import hk.ljx.fishhub.comment.biz.rpc.NoteRpcService;
import hk.ljx.framework.mq.consumer.BatchConsumerFactory;
import hk.ljx.framework.mq.consumer.BatchPushConsumer;
import hk.ljx.framework.mq.tx.TransactionalMqSender;
import hk.ljx.framework.mq.tx.TxJournalStore;
import hk.ljx.fishhub.note.api.NoteWriteAccessCheckReqDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 评论发布批量落库消费者
 */
@Component
@Slf4j
public class Comment2DBConsumer {

    private static final int BATCH_MAX_SIZE = 30;

    private final CommentDOMapper commentDOMapper;
    private final TransactionTemplate transactionTemplate;
    private final TransactionalMqSender transactionalMqSender;
    private final TxJournalStore txJournalStore;
    private final NoteRpcService noteRpcService;
    private final BatchPushConsumer batchPushConsumer;

    // 每秒创建 1000 个令牌
    private final RateLimiter rateLimiter = RateLimiter.create(1000);

    public Comment2DBConsumer(CommentDOMapper commentDOMapper,
                              TransactionTemplate transactionTemplate,
                              TransactionalMqSender transactionalMqSender,
                              TxJournalStore txJournalStore,
                              NoteRpcService noteRpcService,
                              BatchConsumerFactory batchConsumerFactory) throws MQClientException {
        this.commentDOMapper = commentDOMapper;
        this.transactionTemplate = transactionTemplate;
        this.transactionalMqSender = transactionalMqSender;
        this.txJournalStore = txJournalStore;
        this.noteRpcService = noteRpcService;
        this.batchPushConsumer = batchConsumerFactory == null ? null : batchConsumerFactory.create(
                "fishhub_group_" + MQConstants.TOPIC_PUBLISH_COMMENT,
                MQConstants.TOPIC_PUBLISH_COMMENT,
                "*",
                BATCH_MAX_SIZE,
                0,
                BatchConsumerFactory.Mode.CONCURRENTLY,
                this::consume);
    }

    boolean consume(List<MessageExt> msgs) {
        log.info("==> 本批次消息大小: {}", msgs.size());
        try {
            // 令牌桶流控
            rateLimiter.acquire();

            // 消息体 Json 字符串转 DTO
            List<PublishCommentMqDTO> rawReceivedComments = Lists.newArrayList();
            msgs.forEach(msg -> {
                String msgJson = new String(msg.getBody(), StandardCharsets.UTF_8);
                try {
                    PublishCommentMqDTO comment = JsonUtils.parseObject(msgJson, PublishCommentMqDTO.class);
                    if (comment == null || comment.getCommentId() == null
                            || comment.getNoteId() == null || comment.getCreatorId() == null) {
                        log.error("丢弃缺少业务主键的评论消息, msgId={}, payloadSize={}", msg.getMsgId(),
                                msgJson.length());
                        return;
                    }
                    rawReceivedComments.add(comment);
                } catch (Exception e) {
                    log.error("丢弃无法解析的评论消息, msgId={}, payloadSize={}", msg.getMsgId(),
                            msgJson.length(), e);
                }
            });

            // 同一批消息可能包含重复投递，保留第一条。
            Map<Long, PublishCommentMqDTO> uniqueComments = new LinkedHashMap<>();
            for (PublishCommentMqDTO comment : rawReceivedComments) {
                uniqueComments.putIfAbsent(comment.getCommentId(), comment);
            }
            List<PublishCommentMqDTO> receivedComments = new ArrayList<>(uniqueComments.values());
            if (CollUtil.isEmpty(receivedComments)) {
                return true;
            }

            // RocketMQ 是至少一次投递，先过滤已落库的评论，避免重复消费导致重复计数
            List<Long> commentIds = receivedComments.stream()
                    .map(PublishCommentMqDTO::getCommentId)
                    .toList();
            List<CommentDO> existingComments = CollUtil.isEmpty(commentIds)
                    ? Collections.emptyList()
                    : commentDOMapper.selectByCommentIds(commentIds);
            Set<Long> existingCommentIds = existingComments.stream()
                    .map(CommentDO::getId)
                    .collect(Collectors.toSet());
            List<PublishCommentMqDTO> publishCommentMqDTOS = receivedComments.stream()
                    .filter(comment -> !existingCommentIds.contains(comment.getCommentId()))
                    .toList();
            if (CollUtil.isEmpty(publishCommentMqDTOS)) {
                return true;
            }

            // 校验笔记写入权限
            Set<NoteWriteAccessCheckReqDTO> writableAccesses = new HashSet<>(
                    noteRpcService.findWritableNoteAccesses(publishCommentMqDTOS.stream()
                            .map(comment -> NoteWriteAccessCheckReqDTO.builder()
                                    .noteId(comment.getNoteId())
                                    .userId(comment.getCreatorId())
                                    .build())
                            .toList()));
            publishCommentMqDTOS = publishCommentMqDTOS.stream()
                    .filter(comment -> writableAccesses.contains(NoteWriteAccessCheckReqDTO.builder()
                            .noteId(comment.getNoteId())
                            .userId(comment.getCreatorId())
                            .build()))
                    .toList();
            if (CollUtil.isEmpty(publishCommentMqDTOS)) {
                return true;
            }

            // 提取所有不为空的回复评论 ID
            List<Long> replyCommentIds = publishCommentMqDTOS.stream()
                    .filter(publishCommentMqDTO -> Objects.nonNull(publishCommentMqDTO.getReplyCommentId()))
                    .map(PublishCommentMqDTO::getReplyCommentId).toList();

            // 批量查询相关回复评论记录
            List<CommentDO> replyCommentDOS = null;
            if (CollUtil.isNotEmpty(replyCommentIds)) {
                // 查询数据库
                replyCommentDOS = commentDOMapper.selectByCommentIds(replyCommentIds);
            }

            // DO 集合转 <评论 ID - 评论 DO> 字典, 以方便后续查找
            final Map<Long, CommentDO> commentIdAndCommentDOMap = CollUtil.isEmpty(replyCommentDOS)
                    ? Collections.emptyMap()
                    : replyCommentDOS.stream().collect(Collectors.toMap(CommentDO::getId, Function.identity(), (l, r) -> l));

            // 检查是否有回复的父评论尚未落库
            int maxReconsumeTimes = msgs.stream().mapToInt(MessageExt::getReconsumeTimes).max().orElse(0);
            boolean hasMissingReplyTarget = replyCommentIds.stream().anyMatch(id -> !commentIdAndCommentDOMap.containsKey(id));
            if (hasMissingReplyTarget && maxReconsumeTimes < 3) {
                log.info("检测到部分回复目标父评论尚未落库，稍后重试消费, reconsumeTimes={}", maxReconsumeTimes);
                return false;
            }

            // DTO 转 BO
            List<CommentBO> commentBOS = Lists.newArrayList();
            for (PublishCommentMqDTO publishCommentMqDTO : publishCommentMqDTOS) {
                String imageUrl = publishCommentMqDTO.getImageUrl();
                CommentBO commentBO = CommentBO.builder()
                        .id(publishCommentMqDTO.getCommentId())
                        .noteId(publishCommentMqDTO.getNoteId())
                        .userId(publishCommentMqDTO.getCreatorId())
                        .isContentEmpty(true) // 默认评论内容为空
                        .imageUrl(StringUtils.isBlank(imageUrl) ? "" : imageUrl)
                        .level(CommentLevelEnum.ONE.getCode()) // 默认为一级评论
                        .parentId(publishCommentMqDTO.getNoteId()) // 默认设置为所属笔记 ID
                        .createTime(publishCommentMqDTO.getCreateTime())
                        .updateTime(publishCommentMqDTO.getCreateTime())
                        .isTop(false)
                        .replyTotal(0L)
                        .likeTotal(0L)
                        .replyCommentId(0L)
                        .replyUserId(0L)
                        .build();

                // 评论内容若不为空
                String content = publishCommentMqDTO.getContent();
                if (StringUtils.isNotBlank(content)) {
                    commentBO.setContentUuid(UUID.nameUUIDFromBytes(
                            ("comment:" + commentBO.getId()).getBytes(StandardCharsets.UTF_8)).toString());
                    commentBO.setIsContentEmpty(false);
                    commentBO.setContent(content);
                }

                // 设置评论级别、回复用户 ID (reply_user_id)、父评论 ID (parent_id)
                Long replyCommentId = publishCommentMqDTO.getReplyCommentId();
                if (Objects.nonNull(replyCommentId)) {
                    CommentDO replyCommentDO = commentIdAndCommentDOMap.get(replyCommentId);

                    if (replyCommentDO == null || !Objects.equals(replyCommentDO.getNoteId(), commentBO.getNoteId())) {
                        log.info("丢弃无效回复评论消息，commentId={}, replyCommentId={}",
                                commentBO.getId(), replyCommentId);
                        continue;
                    }
                    // 若回复的评论 ID 不为空，说明是二级评论
                    commentBO.setLevel(CommentLevelEnum.TWO.getCode());

                    commentBO.setReplyCommentId(publishCommentMqDTO.getReplyCommentId());
                    // 父评论 ID
                    commentBO.setParentId(replyCommentDO.getId());
                    if (Objects.equals(replyCommentDO.getLevel(), CommentLevelEnum.TWO.getCode())) { // 如果回复的评论属于二级评论
                        commentBO.setParentId(replyCommentDO.getParentId());
                    }
                    // 回复的哪个用户
                    commentBO.setReplyUserId(replyCommentDO.getUserId());
                }

                commentBOS.add(commentBO);
            }

            if (CollUtil.isEmpty(commentBOS)) {
                return true;
            }

            log.info("评论批量入库前校验完成，count={}", commentBOS.size());

            // 发送发布变更事务消息
            List<CommentItemMqDTO> eventItems = commentBOS.stream().map(this::toEventItem).toList();
            String eventBody = JsonUtils.toJsonString(CommentChangedEventMqDTO.builder()
                    .changeType(MQConstants.COMMENT_CHANGE_TYPE_PUBLISH)
                    .items(eventItems)
                    .build());

            List<CommentBO> finalCommentBOS = commentBOS;
            transactionalMqSender.sendInTransaction(MQConstants.TOPIC_COMMENT_CHANGED, eventBody, txId -> {
                int inserted = transactionTemplate.execute(status -> {
                    try {
                        int count = commentDOMapper.batchInsert(finalCommentBOS);
                        if (count != finalCommentBOS.size()) {
                            if (count == 0) {
                                return count;
                            }
                            throw new IllegalStateException("评论批次并发冲突，整体重试");
                        }
                        // 累加子评论数
                        finalCommentBOS.stream()
                                .filter(comment -> Objects.equals(comment.getLevel(), CommentLevelEnum.TWO.getCode()))
                                .collect(Collectors.groupingBy(CommentBO::getParentId))
                                .forEach((parentId, children) ->
                                        commentDOMapper.updateChildCommentTotal(parentId, children.size()));
                        txJournalStore.record(txId);
                        return count;
                    } catch (Exception ex) {
                        status.setRollbackOnly(); // 标记事务为回滚
                        log.error("", ex);
                        throw ex;
                    }
                });
                if (inserted == 0) {
                    log.info("评论批次已被先前投递处理，跳过, count={}", finalCommentBOS.size());
                    return false;
                }
                log.info("评论批量入库完成，count={}", inserted);
                return true;
            });

            return true;
        } catch (Exception e) {
            log.error("评论批量入库消费异常", e);
            return false;
        }
    }

    private CommentItemMqDTO toEventItem(CommentBO comment) {
        return CommentItemMqDTO.builder()
                .id(comment.getId())
                .noteId(comment.getNoteId())
                .level(comment.getLevel())
                .parentId(comment.getParentId())
                .userId(comment.getUserId())
                .contentUuid(comment.getContentUuid())
                .content(comment.getContent())
                .isContentEmpty(comment.getIsContentEmpty())
                .createTime(comment.getCreateTime())
                .build();
    }
}
