package hk.ljx.fishhub.comment.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.RateLimiter;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.enums.CommentLevelEnum;
import hk.ljx.fishhub.comment.biz.model.bo.CommentBO;
import hk.ljx.fishhub.comment.biz.model.dto.CountPublishCommentMqDTO;
import hk.ljx.fishhub.comment.biz.model.dto.InvalidateOneLevelCommentCacheMqDTO;
import hk.ljx.fishhub.comment.biz.model.dto.PublishCommentMqDTO;
import hk.ljx.fishhub.comment.biz.model.dto.SyncCommentContentMqDTO;
import hk.ljx.fishhub.comment.biz.rpc.NoteRpcService;
import hk.ljx.fishhub.comment.biz.retry.SendMqRetryHelper;
import hk.ljx.fishhub.note.api.NoteWriteAccessCheckReqDTO;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;


@Component
@Slf4j
public class Comment2DBConsumer {

    @Value("${rocketmq.name-server}")
    private String namesrvAddr;
    @Resource
    private CommentDOMapper commentDOMapper;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private SendMqRetryHelper sendMqRetryHelper;
    @Resource
    private NoteRpcService noteRpcService;

    private DefaultMQPushConsumer consumer;

    // 每秒创建 1000 个令牌
    private RateLimiter rateLimiter = RateLimiter.create(1000);

    @Bean
    public DefaultMQPushConsumer mqPushConsumer() throws MQClientException {
        // Group 组
        String group = "fishhub_group_" + MQConstants.TOPIC_PUBLISH_COMMENT;

        // 创建一个新的 DefaultMQPushConsumer 实例，并指定消费者的消费组名
        consumer = new DefaultMQPushConsumer(group);

        // 设置 RocketMQ 的 NameServer 地址
        consumer.setNamesrvAddr(namesrvAddr);

        // 订阅指定的主题，并设置主题的订阅规则（"*" 表示订阅所有标签的消息）
        consumer.subscribe(MQConstants.TOPIC_PUBLISH_COMMENT, "*");

        // 设置消费者消费消息的起始位置，如果队列中没有消息，则从最新的消息开始消费。
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);

        // 设置消息消费模式，这里使用集群模式 (CLUSTERING)
        consumer.setMessageModel(MessageModel.CLUSTERING);

        // 设置每批次消费的最大消息数量，这里设置为 30，表示每次拉取时最多消费 30 条消息。
        consumer.setConsumeMessageBatchMaxSize(30);

        // 注册消息监听器
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> consume(msgs));

        // 启动消费者
        consumer.start();
        return consumer;
    }

    ConsumeConcurrentlyStatus consume(List<MessageExt> msgs) {
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
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
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
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }

            // 发布接口只做乐观投递；消费端批量以 MySQL 当前状态裁决笔记是否允许写评论。
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
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
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
            Map<Long, CommentDO> commentIdAndCommentDOMap = Maps.newHashMap();
            if (CollUtil.isNotEmpty(replyCommentDOS)) {
                commentIdAndCommentDOMap = replyCommentDOS.stream().collect(Collectors.toMap(CommentDO::getId, commentDO -> commentDO));
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

                    // 接口不再同步查库，回复目标的存在性和归属由消费者最终校验，不能把非法回复降级为一级评论。
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
                    if (Objects.equals(replyCommentDO.getLevel(), CommentLevelEnum.TWO.getCode())) { // 如果回复的评论属于一级评论
                        commentBO.setParentId(replyCommentDO.getParentId());
                    }
                    // 回复的哪个用户
                    commentBO.setReplyUserId(replyCommentDO.getUserId());
                }

                commentBOS.add(commentBO);
            }

            log.info("评论批量入库前校验完成，count={}", commentBOS.size());

            // 编程式事务，保证整体操作的原子性
            PersistedComments persistedComments = transactionTemplate.execute(status -> {
                try {
                    // 逐条使用 INSERT IGNORE 认领业务主键，避免并发重复消费时重复计数
                    List<CommentBO> inserted = Lists.newArrayList();
                    for (CommentBO commentBO : commentBOS) {
                        if (commentDOMapper.batchInsert(Collections.singletonList(commentBO)) == 1) {
                            inserted.add(commentBO);
                        }
                    }

                    List<String> contentTaskBodies = inserted.stream()
                            .filter(commentBO -> Boolean.FALSE.equals(commentBO.getIsContentEmpty()))
                            .map(this::buildContentTaskBody)
                            .toList();
                    contentTaskBodies.forEach(body ->
                            sendMqRetryHelper.enqueue(MQConstants.TOPIC_SYNC_COMMENT_CONTENT, body));

                    String countEventBody = null;
                    if (CollUtil.isNotEmpty(inserted)) {
                        List<CountPublishCommentMqDTO> countEvents = inserted.stream()
                                .map(commentBO -> CountPublishCommentMqDTO.builder()
                                        .noteId(commentBO.getNoteId())
                                        .commentId(commentBO.getId())
                                        .level(commentBO.getLevel())
                                        .parentId(commentBO.getParentId())
                                        .build())
                                .toList();
                        countEventBody = JsonUtils.toJsonString(countEvents);
                        sendMqRetryHelper.enqueue(MQConstants.TOPIC_COUNT_NOTE_COMMENT, countEventBody);
                    }

                    List<String> cacheInvalidationBodies = inserted.stream()
                            .filter(comment -> Objects.equals(comment.getLevel(), CommentLevelEnum.ONE.getCode()))
                            .map(CommentBO::getNoteId)
                            .distinct()
                            .map(noteId -> JsonUtils.toJsonString(InvalidateOneLevelCommentCacheMqDTO.builder()
                                    .eventId(UUID.randomUUID().toString())
                                    .noteId(noteId)
                                    .build()))
                            .toList();
                    cacheInvalidationBodies.forEach(body -> sendMqRetryHelper.enqueue(
                            MQConstants.TOPIC_INVALIDATE_ONE_LEVEL_COMMENT_CACHE, body));

                    return new PersistedComments(inserted, countEventBody, contentTaskBodies,
                            cacheInvalidationBodies);
                } catch (Exception ex) {
                    status.setRollbackOnly(); // 标记事务为回滚
                    log.error("", ex);
                    throw ex;
                }
            });

            List<CommentBO> insertedCommentBOS = Objects.requireNonNull(persistedComments).comments();

            // 只处理本批次真正落库成功的评论，防止并发重复消费导致重复计数
            if (CollUtil.isNotEmpty(insertedCommentBOS)) {
                // 事件已经随评论记录一起提交；这里只做提交后的即时投递。
                persistedComments.contentTaskBodies().forEach(body ->
                        sendMqRetryHelper.sendNow(MQConstants.TOPIC_SYNC_COMMENT_CONTENT, body));
                sendMqRetryHelper.sendNow(
                        MQConstants.TOPIC_COUNT_NOTE_COMMENT,
                        persistedComments.countEventBody());
                persistedComments.cacheInvalidationBodies().forEach(body -> sendMqRetryHelper.sendNow(
                        MQConstants.TOPIC_INVALIDATE_ONE_LEVEL_COMMENT_CACHE, body));

            }

            // 手动 ACK，告诉 RocketMQ 这批次消息消费成功
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        } catch (Exception e) {
            log.error("", e);
            // 手动 ACK，告诉 RocketMQ 这批次消息处理失败，稍后再进行重试
            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        }
    }

    private String buildContentTaskBody(CommentBO comment) {
        return JsonUtils.toJsonString(SyncCommentContentMqDTO.builder()
                .commentId(comment.getId())
                .noteId(comment.getNoteId())
                .createTime(comment.getCreateTime())
                .contentUuid(comment.getContentUuid())
                .content(comment.getContent())
                .build());
    }

    private record PersistedComments(List<CommentBO> comments, String countEventBody,
                                     List<String> contentTaskBodies, List<String> cacheInvalidationBodies) {
    }

    @PreDestroy
    public void destroy() {
        if (Objects.nonNull(consumer)) {
            try {
                consumer.shutdown();  // 关闭消费者
            } catch (Exception e) {
                log.error("", e);
            }
        }
    }

}
