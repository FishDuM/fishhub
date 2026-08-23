package hk.ljx.fishhub.user.relation.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.user.relation.biz.cache.RelationListCacheService;
import hk.ljx.fishhub.user.relation.biz.constant.MQConstants;
import hk.ljx.fishhub.user.relation.biz.domain.dataobject.FollowingDO;
import hk.ljx.fishhub.user.relation.biz.domain.mapper.FollowingDOMapper;
import hk.ljx.fishhub.user.relation.biz.enums.FollowUnfollowTypeEnum;
import hk.ljx.fishhub.count.dto.CountFollowUnfollowMqDTO;
import hk.ljx.fishhub.user.relation.biz.model.dto.FollowUserMqDTO;
import hk.ljx.fishhub.user.relation.biz.model.dto.UnfollowUserMqDTO;
import hk.ljx.framework.mq.tx.TransactionalMqSender;
import hk.ljx.framework.mq.tx.TxJournalStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 关注 / 取关消费者。
 * 借助事务消息保证数据库关系表与下游计数消息原子提交，避免重试时计数消息永久丢失。
 */
@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_FOLLOW_OR_UNFOLLOW, // Group 组
        topic = MQConstants.TOPIC_FOLLOW_OR_UNFOLLOW, // 消费的主题 Topic
        consumeMode = ConsumeMode.ORDERLY // 设置为顺序消费模式
)
@Slf4j
@RequiredArgsConstructor
public class FollowUnfollowConsumer implements RocketMQListener<Message> {

    private final FollowingDOMapper followingDOMapper;
    private final TransactionTemplate transactionTemplate;
    private final RelationListCacheService relationListCacheService;
    private final TransactionalMqSender transactionalMqSender;
    private final TxJournalStore txJournalStore;

    @Override
    public void onMessage(Message message) {
        // 消息体
        String bodyJsonStr = new String(message.getBody(), StandardCharsets.UTF_8);
        // 标签
        String tags = message.getTags();

        log.info("消费关注关系事件，tags={}", tags);

        // 根据 MQ 标签，判断操作类型
        if (Objects.equals(tags, MQConstants.TAG_FOLLOW)) { // 关注
            handleFollowTagMessage(bodyJsonStr);
        } else if (Objects.equals(tags, MQConstants.TAG_UNFOLLOW)) { // 取关
            handleUnfollowTagMessage(bodyJsonStr);
        }
    }

    /**
     * 关注
     *
     * @param bodyJsonStr
     */
    private void handleFollowTagMessage(String bodyJsonStr) {
        // 将消息体 Json 字符串转为 DTO 对象
        FollowUserMqDTO followUserMqDTO = JsonUtils.parseObject(bodyJsonStr, FollowUserMqDTO.class);

        // 判空
        if (Objects.isNull(followUserMqDTO)) return;

        Long userId = followUserMqDTO.getUserId();
        Long followUserId = followUserMqDTO.getFollowUserId();
        LocalDateTime createTime = followUserMqDTO.getCreateTime();

        if (userId == null || followUserId == null || createTime == null) {
            log.error("丢弃无法恢复的关注消息：必要字段缺失");
            return;
        }

        CountFollowUnfollowMqDTO countEvent = CountFollowUnfollowMqDTO.builder()
                .userId(userId)
                .targetUserId(followUserId)
                .type(FollowUnfollowTypeEnum.FOLLOW.getCode())
                .createTime(createTime)
                .build();

        transactionalMqSender.sendInTransaction(MQConstants.TOPIC_COUNT_FOLLOWING,
                JsonUtils.toJsonString(countEvent),
                txId -> Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                    int count = followingDOMapper.insertIgnore(FollowingDO.builder()
                            .userId(userId)
                            .followingUserId(followUserId)
                            .createTime(createTime)
                            .build());
                    if (count > 0) {
                        txJournalStore.record(txId);
                        return true;
                    }
                    return false;
                })));

        // 反向粉丝列表增量维护（尽力而为，失败由读侧重建兜底）
        relationListCacheService.addFan(followUserId, userId, createTime);
        log.info("关注关系落库与计数事务消息发送完成, userId={}, targetUserId={}", userId, followUserId);
    }

    /**
     * 取关
     *
     * @param bodyJsonStr
     */
    private void handleUnfollowTagMessage(String bodyJsonStr) {
        // 将消息体 Json 字符串转为 DTO 对象
        UnfollowUserMqDTO unfollowUserMqDTO = JsonUtils.parseObject(bodyJsonStr, UnfollowUserMqDTO.class);

        // 判空
        if (Objects.isNull(unfollowUserMqDTO)) return;

        Long userId = unfollowUserMqDTO.getUserId();
        Long unfollowUserId = unfollowUserMqDTO.getUnfollowUserId();
        LocalDateTime createTime = unfollowUserMqDTO.getCreateTime();

        if (userId == null || unfollowUserId == null || createTime == null) {
            log.error("丢弃无法恢复的取关消息：必要字段缺失");
            return;
        }

        CountFollowUnfollowMqDTO countEvent = CountFollowUnfollowMqDTO.builder()
                .userId(userId)
                .targetUserId(unfollowUserId)
                .type(FollowUnfollowTypeEnum.UNFOLLOW.getCode())
                .createTime(createTime)
                .build();

        transactionalMqSender.sendInTransaction(MQConstants.TOPIC_COUNT_FOLLOWING,
                JsonUtils.toJsonString(countEvent),
                txId -> Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                    int count = followingDOMapper.deleteByUserIdAndFollowingUserId(userId, unfollowUserId);
                    if (count > 0) {
                        txJournalStore.record(txId);
                        return true;
                    }
                    return false;
                })));

        // 反向粉丝列表增量维护（尽力而为，失败由读侧重建兜底）
        relationListCacheService.removeFan(unfollowUserId, userId);
        log.info("取关关系落库与计数事务消息发送完成, userId={}, targetUserId={}", userId, unfollowUserId);
    }
}
