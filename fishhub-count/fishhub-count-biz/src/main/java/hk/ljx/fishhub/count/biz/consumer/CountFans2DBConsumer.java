package hk.ljx.fishhub.count.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.count.biz.constant.MQConstants;
import hk.ljx.fishhub.count.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.count.biz.domain.mapper.UserCountDOMapper;
import hk.ljx.fishhub.count.biz.model.dto.AggregationCountFansMqDTO;
import hk.ljx.fishhub.count.biz.service.MqIdempotentExecutor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_COUNT_FANS_2_DB,
        topic = MQConstants.TOPIC_COUNT_FANS_2_DB)
@Slf4j
public class CountFans2DBConsumer implements RocketMQListener<String> {

    @Resource
    private UserCountDOMapper userCountDOMapper;
    @Resource
    private MqIdempotentExecutor mqIdempotentExecutor;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onMessage(String body) {
        List<AggregationCountFansMqDTO> aggregates;
        try {
            aggregates = JsonUtils.parseList(body, AggregationCountFansMqDTO.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("粉丝计数消息格式错误", e);
        }
        if (CollUtil.isEmpty(aggregates)
                || aggregates.stream().anyMatch(item -> item.getTargetUserId() == null
                || item.getCount() == null || item.getBatchId() == null)) {
            throw new IllegalArgumentException("粉丝计数消息为空或格式错误");
        }

        boolean applied = mqIdempotentExecutor.execute("count-fans-2db", body,
                () -> aggregates.forEach(item -> userCountDOMapper.insertOrUpdateFansTotalByUserId(
                        item.getCount(), item.getTargetUserId())));

        // 无论首次消费还是重复投递，都尝试清掉旧缓存；查询链路会从已提交的 MySQL 重建。
        redisTemplate.delete(aggregates.stream()
                .map(item -> RedisKeyConstants.buildCountUserKey(item.getTargetUserId()))
                .distinct()
                .toList());
        if (!applied) {
            log.info("粉丝计数消息已处理，忽略重复入库");
        }
    }
}
