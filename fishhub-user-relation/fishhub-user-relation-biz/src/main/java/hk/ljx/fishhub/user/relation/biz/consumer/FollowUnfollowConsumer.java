package hk.ljx.fishhub.user.relation.biz.consumer;

import com.google.common.util.concurrent.RateLimiter;
import cn.hutool.crypto.digest.DigestUtil;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.framework.mq.tx.TransactionalMqSender;
import hk.ljx.framework.mq.tx.TxJournalStore;
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
    private TransactionalMqSender transactionalMqSender;
    @Resource
    private TxJournalStore txJournalStore;

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

        // 关系状态迁移与变更事件经由事务消息原子绑定；重复消费或状态未变化时不登记 journal，半消息回滚。
        transactionalMqSender.sendInTransaction(MQConstants.TOPIC_USER_RELATION_CHANGED, countEventBody, txId -> {
            Boolean applied = transactionTemplate.execute(status -> {
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
                txJournalStore.record(txId);
                return true;
            });
            log.info("关注关系事务完成, userId={}, targetUserId={}, applied={}", userId, followUserId, applied);
            return applied;
        });

        // MySQL 是关系事实源。重复投递也执行缓存失效，确保 Redis 暂时不可用后仍能靠 MQ 重试恢复。
        redisTemplate.delete(RedisKeyConstants.buildUserFansKey(followUserId));
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

        transactionalMqSender.sendInTransaction(MQConstants.TOPIC_USER_RELATION_CHANGED, countEventBody, txId -> {
            Boolean applied = transactionTemplate.execute(status -> {
                if (mqConsumeRecordMapper.insert("follow-unfollow", messageKey) != 1) {
                    return false;
                }

                int count = followingDOMapper.deleteByUserIdAndFollowingUserId(userId, unfollowUserId);
                fansDOMapper.deleteByUserIdAndFansUserId(unfollowUserId, userId);
                if (count != 1) {
                    return false;
                }
                txJournalStore.record(txId);
                return true;
            });
            log.info("取关关系事务完成, userId={}, targetUserId={}, applied={}", userId, unfollowUserId, applied);
            return applied;
        });

        redisTemplate.delete(RedisKeyConstants.buildUserFansKey(unfollowUserId));
    }

}
