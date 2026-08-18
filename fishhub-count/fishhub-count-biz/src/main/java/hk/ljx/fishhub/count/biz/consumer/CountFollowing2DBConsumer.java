package hk.ljx.fishhub.count.biz.consumer;

import com.google.common.util.concurrent.RateLimiter;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.count.biz.constant.MQConstants;
import hk.ljx.fishhub.count.biz.domain.mapper.UserCountDOMapper;
import hk.ljx.fishhub.count.biz.enums.FollowUnfollowTypeEnum;
import hk.ljx.fishhub.count.dto.CountFollowUnfollowMqDTO;
import hk.ljx.framework.mq.idempotent.MqIdempotentExecutor;
import hk.ljx.fishhub.count.biz.service.UserCountCacheVersionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
        topic = MQConstants.TOPIC_COUNT_FOLLOWING // 主题 Topic
        )
@Slf4j
public class CountFollowing2DBConsumer implements RocketMQListener<String> {

    @Resource
    private UserCountDOMapper userCountDOMapper;
    @Resource
    private MqIdempotentExecutor mqIdempotentExecutor;
    @Resource
    private UserCountCacheVersionService userCountCacheVersionService;

    // 每秒创建 5000 个令牌
    private RateLimiter rateLimiter = RateLimiter.create(5000);

    @Override
    public void onMessage(String body) {
        // 流量削峰：通过获取令牌，如果没有令牌可用，将阻塞，直到获得
        rateLimiter.acquire();

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

        boolean applied = mqIdempotentExecutor.execute("count-following-2db", body, () -> {
            userCountDOMapper.insertOrUpdateFollowingTotalByUserId(delta, event.getUserId());
            userCountDOMapper.insertOrUpdateFansTotalByUserId(delta, event.getTargetUserId());
        });

        // 无论是否重复投递，都推进版本，确保旧缓存快照不再被读取。
        userCountCacheVersionService.advanceVersion(event.getUserId());
        userCountCacheVersionService.advanceVersion(event.getTargetUserId());
        if (!applied) {
            log.info("关注计数消息已处理，忽略重复投递");
        }
    }

}
