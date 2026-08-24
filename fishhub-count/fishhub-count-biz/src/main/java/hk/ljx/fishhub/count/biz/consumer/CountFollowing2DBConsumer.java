package hk.ljx.fishhub.count.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.count.biz.constant.MQConstants;
import hk.ljx.fishhub.count.biz.domain.mapper.UserCountDOMapper;
import hk.ljx.fishhub.count.biz.enums.FollowUnfollowTypeEnum;
import hk.ljx.fishhub.count.dto.CountFollowUnfollowMqDTO;
import hk.ljx.framework.mq.idempotent.MqIdempotentExecutor;
import hk.ljx.fishhub.count.biz.service.UserCountCacheVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Objects;


/**
 * 关注数 / 粉丝数统一入库消费者。
 * 一条关注事件同时累加关注者 following_total 与被关注者 fans_total。
 */
@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_COUNT_FOLLOWING, // Group 组
        topic = MQConstants.TOPIC_COUNT_FOLLOWING, // 主题 Topic
        consumeMode = ConsumeMode.ORDERLY
        )
@Slf4j
@RequiredArgsConstructor
public class CountFollowing2DBConsumer implements RocketMQListener<String> {

    private final UserCountDOMapper userCountDOMapper;
    private final MqIdempotentExecutor mqIdempotentExecutor;
    private final UserCountCacheVersionService userCountCacheVersionService;

    @Override
    public void onMessage(String body) {
        if (StringUtils.isBlank(body)) {
            throw new IllegalArgumentException("关注计数消息为空");
        }

        CountFollowUnfollowMqDTO event = JsonUtils.parseObject(body, CountFollowUnfollowMqDTO.class);
        if (event == null || event.getUserId() == null || event.getTargetUserId() == null
                || event.getType() == null || event.getCreateTime() == null) {
            throw new IllegalArgumentException("关注计数消息缺少必要字段");
        }

        // 操作类型：关注 or 取关
        Integer type = event.getType();
        // 关注数：关注 +1， 取关 -1
        int delta = Objects.equals(type, FollowUnfollowTypeEnum.FOLLOW.getCode()) ? 1 : -1;

        Long userId = event.getUserId();
        Long targetUserId = event.getTargetUserId();

        boolean applied = mqIdempotentExecutor.execute("count-following-2db", body, () -> {
            // 比较两个用户的 ID 大小，按 ID 升序执行更新以避免 MySQL 行锁交叉死锁
            if (userId < targetUserId) {
                // 如果操作者 ID 比较小（比如 100 关注 200）：
                userCountDOMapper.insertOrUpdateFollowingTotalByUserId(delta, userId);       // 先更新/锁住 100
                userCountDOMapper.insertOrUpdateFansTotalByUserId(delta, targetUserId);      // 后更新/锁住 200
            } else {
                // 如果操作者 ID 比较大（比如 200 关注 100）：
                userCountDOMapper.insertOrUpdateFansTotalByUserId(delta, targetUserId);      // 先更新/锁住 100
                userCountDOMapper.insertOrUpdateFollowingTotalByUserId(delta, userId);       // 后更新/锁住 200
            }
        });

        // 无论是否重复投递，都推进版本，确保旧缓存快照不再被读取。
        userCountCacheVersionService.advanceVersion(event.getUserId());
        userCountCacheVersionService.advanceVersion(event.getTargetUserId());
        if (!applied) {
            log.info("关注计数消息已处理，忽略重复投递");
        }
    }

}
