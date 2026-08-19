package hk.ljx.fishhub.user.relation.biz.consumer;

import com.google.common.util.concurrent.RateLimiter;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.user.relation.biz.cache.RelationListCacheService;
import hk.ljx.fishhub.user.relation.biz.constant.MQConstants;
import hk.ljx.fishhub.user.relation.biz.domain.dataobject.FollowingDO;
import hk.ljx.fishhub.user.relation.biz.domain.mapper.FollowingDOMapper;
import hk.ljx.fishhub.user.relation.biz.enums.FollowUnfollowTypeEnum;
import hk.ljx.fishhub.count.dto.CountFollowUnfollowMqDTO;
import hk.ljx.fishhub.user.relation.biz.model.dto.FollowUserMqDTO;
import hk.ljx.fishhub.user.relation.biz.model.dto.UnfollowUserMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 关注 / 取关消费者。
 * 幂等由唯一键 uk(user_id, following_user_id) 保证；关系写入成功后维护反向粉丝 ZSet 并发统一计数事件。
 */
@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_FOLLOW_OR_UNFOLLOW, // Group 组
        topic = MQConstants.TOPIC_FOLLOW_OR_UNFOLLOW, // 消费的主题 Topic
        consumeMode = ConsumeMode.ORDERLY // 设置为顺序消费模式
)
@Slf4j
public class FollowUnfollowConsumer implements RocketMQListener<Message> {

    @Resource
    private FollowingDOMapper followingDOMapper;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private RateLimiter rateLimiter;
    @Resource
    private RocketMQTemplate rocketMQTemplate;
    @Resource
    private RelationListCacheService relationListCacheService;

    @Override
    public void onMessage(Message message) {
        // 流量削峰：通过获取令牌，如果没有令牌可用，将阻塞，直到获得
        rateLimiter.acquire();

        // 消息体
        String bodyJsonStr = new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8);
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

        // 重复投递 / 已关注时 insertIgnore 返回 0，视为状态未变化，不再发计数事件
        boolean applied = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            int count = followingDOMapper.insertIgnore(FollowingDO.builder()
                    .userId(userId)
                    .followingUserId(followUserId)
                    .createTime(createTime)
                    .build());
            return count == 1;
        }));

        if (applied) {
            // 反向粉丝列表增量维护（尽力而为，失败由读侧重建兜底）
            relationListCacheService.addFan(followUserId, userId, createTime);
            sendCountEvent(userId, followUserId, FollowUnfollowTypeEnum.FOLLOW.getCode(), createTime);
        }
        log.info("关注关系落库完成, userId={}, targetUserId={}, applied={}", userId, followUserId, applied);
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

        // 取关不存在的关注关系返回 0，视为状态未变化，不再发计数事件
        boolean applied = Boolean.TRUE.equals(transactionTemplate.execute(status ->
                followingDOMapper.deleteByUserIdAndFollowingUserId(userId, unfollowUserId) == 1));

        if (applied) {
            // 反向粉丝列表增量维护（尽力而为，失败由读侧重建兜底）
            relationListCacheService.removeFan(unfollowUserId, userId);
            sendCountEvent(userId, unfollowUserId, FollowUnfollowTypeEnum.UNFOLLOW.getCode(), createTime);
        }
        log.info("取关关系落库完成, userId={}, targetUserId={}, applied={}", userId, unfollowUserId, applied);
    }

    /**
     * 发送统一关系计数事件（关注 +1 / 粉丝 +1 由 count 服务一次消费）
     */
    private void sendCountEvent(Long userId, Long targetUserId, Integer type, LocalDateTime createTime) {
        CountFollowUnfollowMqDTO countEvent = CountFollowUnfollowMqDTO.builder()
                .userId(userId)
                .targetUserId(targetUserId)
                .type(type)
                .createTime(createTime)
                .build();
        Exception lastEx = null;
        for (int i = 0; i < 3; i++) {
            try {
                rocketMQTemplate.syncSendOrderly(MQConstants.TOPIC_COUNT_FOLLOWING,
                        MessageBuilder.withPayload(JsonUtils.toJsonString(countEvent)).build(),
                        String.valueOf(userId));
                return;
            } catch (Exception e) {
                lastEx = e;
                log.warn("关注/取关计数事件发送失败，正在进行第 {} 次重试, userId={}, targetUserId={}",
                        i + 1, userId, targetUserId, e);
            }
        }
        log.error("关注/取关计数事件重试 3 次仍发送失败, userId={}, targetUserId={}", userId, targetUserId, lastEx);
    }

}
