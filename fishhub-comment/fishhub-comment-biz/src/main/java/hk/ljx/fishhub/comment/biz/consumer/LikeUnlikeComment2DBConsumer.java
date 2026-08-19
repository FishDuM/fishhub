package hk.ljx.fishhub.comment.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.RateLimiter;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.enums.LikeUnlikeCommentTypeEnum;
import hk.ljx.fishhub.comment.biz.model.dto.LikeUnlikeCommentMqDTO;
import hk.ljx.fishhub.comment.biz.rpc.NoteRpcService;
import hk.ljx.fishhub.comment.biz.service.CommentLikePersistenceService;
import hk.ljx.fishhub.note.api.NoteWriteAccessCheckReqDTO;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
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


@Component
@Slf4j
@RequiredArgsConstructor
public class LikeUnlikeComment2DBConsumer {

    @Value("${rocketmq.name-server}")
    private String namesrvAddr;

    private final CommentLikePersistenceService persistenceService;
    private final CommentDOMapper commentDOMapper;
    private final NoteRpcService noteRpcService;
    private final RocketMQTemplate rocketMQTemplate;

    private DefaultMQPushConsumer consumer;

    // 每秒创建 5000 个令牌
    private final RateLimiter rateLimiter = RateLimiter.create(5000);

    @Bean(name = "LikeUnlikeComment2DBConsumer")
    public DefaultMQPushConsumer mqPushConsumer() throws MQClientException {
        // Group 组
        String group = "fishhub_group_" + MQConstants.TOPIC_COMMENT_LIKE_OR_UNLIKE;

        // 创建一个新的 DefaultMQPushConsumer 实例，并指定消费者的消费组名
        consumer = new DefaultMQPushConsumer(group);

        // 设置 RocketMQ 的 NameServer 地址
        consumer.setNamesrvAddr(namesrvAddr);

        // 订阅指定的主题，并设置主题的订阅规则（"*" 表示订阅所有标签的消息）
        consumer.subscribe(MQConstants.TOPIC_COMMENT_LIKE_OR_UNLIKE, "*");

        // 设置消费者消费消息的起始位置，如果队列中没有消息，则从最新的消息开始消费。
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);

        // 设置消息消费模式，这里使用集群模式 (CLUSTERING)
        consumer.setMessageModel(MessageModel.CLUSTERING);

        // 最大重试次数, 以防消息重试过多次仍然没有成功，避免消息卡在消费队列中。
        consumer.setMaxReconsumeTimes(16);
        // 设置每批次消费的最大消息数量，这里设置为 30，表示每次拉取时最多消费 30 条消息。
        consumer.setConsumeMessageBatchMaxSize(30);

        // 注册消息监听器
        consumer.registerMessageListener((MessageListenerOrderly) (msgs, context) -> {
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
                    return ConsumeOrderlyStatus.SUCCESS;
                }

                // 按评论 ID 分组
                Map<Long, List<LikeUnlikeCommentMqDTO>> commentIdAndListMap = likeUnlikeCommentMqDTOS.stream()
                        .collect(Collectors.groupingBy(LikeUnlikeCommentMqDTO::getCommentId));

                List<LikeUnlikeCommentMqDTO> finalLikeUnlikeCommentMqDTOS = Lists.newArrayList();

                commentIdAndListMap.forEach((commentId, ops) -> {
                    // 优化：若某个用户对某评论，多次操作，如点赞 -> 取消点赞 -> 点赞，需进行操作合并，只提取最后一次操作，进一步降低操作数据库的频率
                    Map<Long, LikeUnlikeCommentMqDTO> userLastOp = ops.stream()
                            .collect(Collectors.toMap(
                                    LikeUnlikeCommentMqDTO::getUserId, // 以发布评论的用户 ID 作为 Map 的键
                                    Function.identity(), // 直接使用 DTO 对象本身作为 Map 的值
                                    // 合并策略：当出现重复键（同一用户多次操作）时，保留时间更晚的记录
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

                // 批量落库（≤30 一批，单事务）：关系行 + like_total 按真实影响行数累加，重复消费天然幂等
                if (CollUtil.isNotEmpty(persistOps)) {
                    appliedCommentIds.addAll(persistenceService.applyBatch(persistOps));
                }

                // 批末触发热度重算（点赞计数已由请求侧实时写入 Redis，无需再删 count:comment 缓存）
                if (CollUtil.isNotEmpty(appliedCommentIds)) {
                    Message<String> heatMessage = MessageBuilder.withPayload(JsonUtils.toJsonString(appliedCommentIds)).build();
                    rocketMQTemplate.syncSend(MQConstants.TOPIC_COMMENT_HEAT_UPDATE, heatMessage);
                }

                // 手动 ACK，告诉 RocketMQ 这批次消息消费成功
                return ConsumeOrderlyStatus.SUCCESS;
            } catch (Exception e) {
                log.error("", e);
                // 这样 RocketMQ 会暂停当前队列的消费一段时间，再重试
                return ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT;
            }
        });

        // 启动消费者
        consumer.start();
        return consumer;
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
