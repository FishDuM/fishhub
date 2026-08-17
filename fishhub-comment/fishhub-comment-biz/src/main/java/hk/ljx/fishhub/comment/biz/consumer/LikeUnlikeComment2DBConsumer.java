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
import hk.ljx.framework.mq.tx.TransactionalMqSender;
import hk.ljx.fishhub.note.api.NoteWriteAccessCheckReqDTO;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;


@Component
@Slf4j
public class LikeUnlikeComment2DBConsumer {

    @Value("${rocketmq.name-server}")
    private String namesrvAddr;

    @Resource
    private CommentLikePersistenceService persistenceService;
    @Resource
    private TransactionalMqSender transactionalMqSender;
    @Resource
    private CommentDOMapper commentDOMapper;
    @Resource
    private NoteRpcService noteRpcService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private DefaultMQPushConsumer consumer;

    // 每秒创建 5000 个令牌
    private RateLimiter rateLimiter = RateLimiter.create(5000);

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

                for (LikeUnlikeCommentMqDTO operation : finalLikeUnlikeCommentMqDTOS) {
                    CommentDO comment = comments.get(operation.getCommentId());
                    if (comment == null) {
                        if (Objects.equals(operation.getType(), LikeUnlikeCommentTypeEnum.LIKE.getCode())) {
                            stringRedisTemplate.delete(RedisKeyConstants.buildBloomCommentLikesKey(operation.getUserId()));
                            throw new IllegalStateException("点赞落库时评论不存在(可能尚未提交)，等待重试, commentId=" + operation.getCommentId());
                        }
                        // UNLIKE：评论不存在时本就是无操作，清布隆后丢弃即可
                        stringRedisTemplate.delete(RedisKeyConstants.buildBloomCommentLikesKey(operation.getUserId()));
                        continue;
                    }
                    if (Objects.equals(operation.getType(), LikeUnlikeCommentTypeEnum.LIKE.getCode())
                            && !writableAccesses.contains(NoteWriteAccessCheckReqDTO.builder()
                            .noteId(comment.getNoteId())
                            .userId(operation.getUserId())
                            .build())) {
                        // 点赞请求已乐观写入布隆过滤器；拒绝时清空用户缓存，后续刷新自动恢复真实状态。
                        stringRedisTemplate.delete(RedisKeyConstants.buildBloomCommentLikesKey(operation.getUserId()));
                        log.info("丢弃不可写笔记上的评论点赞，commentId={}, userId={}",
                                operation.getCommentId(), operation.getUserId());
                        continue;
                    }
                    String eventBody = JsonUtils.toJsonString(operation);
                    // 计数事件与点赞关系落库经由事务消息原子绑定；关系未变化时不登记 journal，半消息回滚丢弃
                    transactionalMqSender.sendInTransaction(MQConstants.TOPIC_APPLIED_COMMENT_LIKE_OR_UNLIKE,
                            eventBody, txId -> persistenceService.apply(operation, txId));
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
