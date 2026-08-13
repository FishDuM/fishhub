package hk.ljx.fishhub.user.relation.biz.consumer;

import com.google.common.util.concurrent.RateLimiter;
import cn.hutool.crypto.digest.DigestUtil;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.user.relation.biz.constant.MQConstants;
import hk.ljx.fishhub.user.relation.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.user.relation.biz.domain.dataobject.FansDO;
import hk.ljx.fishhub.user.relation.biz.domain.dataobject.FollowingDO;
import hk.ljx.fishhub.user.relation.biz.domain.mapper.FansDOMapper;
import hk.ljx.fishhub.user.relation.biz.domain.mapper.FollowingDOMapper;
import hk.ljx.fishhub.user.relation.biz.domain.mapper.MqConsumeRecordMapper;
import hk.ljx.fishhub.user.relation.biz.enums.FollowUnfollowTypeEnum;
import hk.ljx.fishhub.user.relation.biz.model.dto.CountFollowUnfollowMqDTO;
import hk.ljx.fishhub.user.relation.biz.model.dto.FollowUserMqDTO;
import hk.ljx.fishhub.user.relation.biz.model.dto.UnfollowUserMqDTO;
import hk.ljx.fishhub.user.relation.biz.retry.ReliableMqOutbox;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Objects;


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
    private FansDOMapper fansDOMapper;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private RateLimiter rateLimiter;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private MqConsumeRecordMapper mqConsumeRecordMapper;
    @Resource
    private ReliableMqOutbox reliableMqOutbox;

    @Override
    public void onMessage(Message message) {
        // 流量削峰：通过获取令牌，如果没有令牌可用，将阻塞，直到获得
        rateLimiter.acquire();

        // 消息体
        String bodyJsonStr = new String(message.getBody());
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
        String countEventBody = JsonUtils.toJsonString(countEvent);
        String messageKey = DigestUtil.sha256Hex("follow:" + bodyJsonStr);

        // 消费记录、关系状态迁移和待发送计数事件在同一个 MySQL 事务中提交。
        boolean isSuccess = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            if (mqConsumeRecordMapper.insert("follow-unfollow", messageKey) != 1) {
                return false;
            }

            int count = followingDOMapper.insertIgnore(FollowingDO.builder()
                    .userId(userId)
                    .followingUserId(followUserId)
                    .createTime(createTime)
                    .build());
            fansDOMapper.insertIgnore(FansDO.builder()
                    .userId(followUserId)
                    .fansUserId(userId)
                    .createTime(createTime)
                    .build());

            // t_following 是关注状态的主记录；已经存在时不重复产生计数事件。
            if (count != 1) {
                return false;
            }

            reliableMqOutbox.enqueue(MQConstants.TOPIC_COUNT_FOLLOWING, countEventBody,
                    followingOrderingKey(userId));
            reliableMqOutbox.enqueue(MQConstants.TOPIC_COUNT_FANS, countEventBody,
                    fansOrderingKey(followUserId));
            return true;
        }));

        // MySQL 是关系事实源。重复投递也执行缓存失效，确保 Redis 暂时不可用后仍能靠 MQ 重试恢复。
        redisTemplate.delete(RedisKeyConstants.buildUserFansKey(followUserId));

        if (isSuccess) {
            sendCountEvent(countEventBody, userId, followUserId);
        }
    }

    /**
     * 取关
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
        String countEventBody = JsonUtils.toJsonString(countEvent);
        String messageKey = DigestUtil.sha256Hex("unfollow:" + bodyJsonStr);

        boolean isSuccess = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            if (mqConsumeRecordMapper.insert("follow-unfollow", messageKey) != 1) {
                return false;
            }

            int count = followingDOMapper.deleteByUserIdAndFollowingUserId(userId, unfollowUserId);
            fansDOMapper.deleteByUserIdAndFansUserId(unfollowUserId, userId);
            if (count != 1) {
                return false;
            }

            reliableMqOutbox.enqueue(MQConstants.TOPIC_COUNT_FOLLOWING, countEventBody,
                    followingOrderingKey(userId));
            reliableMqOutbox.enqueue(MQConstants.TOPIC_COUNT_FANS, countEventBody,
                    fansOrderingKey(unfollowUserId));
            return true;
        }));

        redisTemplate.delete(RedisKeyConstants.buildUserFansKey(unfollowUserId));

        if (isSuccess) {
            sendCountEvent(countEventBody, userId, unfollowUserId);
        }
    }

    /**
     * 发送 MQ 通知计数服务
     *
     */
    private void sendCountEvent(String body, Long userId, Long targetUserId) {
        // 事件已经在事务中进入 outbox；即时发送失败时由定时任务继续补发。
        reliableMqOutbox.sendNow(MQConstants.TOPIC_COUNT_FOLLOWING, body, followingOrderingKey(userId));
        reliableMqOutbox.sendNow(MQConstants.TOPIC_COUNT_FANS, body, fansOrderingKey(targetUserId));
    }

    private String followingOrderingKey(Long userId) {
        return MQConstants.TOPIC_COUNT_FOLLOWING + ':' + userId;
    }

    private String fansOrderingKey(Long userId) {
        return MQConstants.TOPIC_COUNT_FANS + ':' + userId;
    }

}
