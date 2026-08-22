package hk.ljx.fishhub.search.biz.consumer;

import hk.ljx.framework.common.constant.MqTopicConstants;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.search.biz.service.EsIndexSyncAggregator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 关注/取关计数事件 → 关注者与被关注者索引计数同步。
 */
@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_search_es_count_following",
        topic = MqTopicConstants.TOPIC_COUNT_FOLLOWING)
@Slf4j
@RequiredArgsConstructor
public class CountFollowingEsSyncConsumer implements RocketMQListener<String> {

    private final EsIndexSyncAggregator esIndexSyncAggregator;

    @Override
    public void onMessage(String body) {
        Map<?, ?> event = JsonUtils.parseObject(body, Map.class);
        if (event == null || event.get("userId") == null || event.get("targetUserId") == null) {
            throw new IllegalArgumentException("关注计数消息缺少必要字段");
        }
        esIndexSyncAggregator.submitUser(Long.valueOf(String.valueOf(event.get("userId"))));
        esIndexSyncAggregator.submitUser(Long.valueOf(String.valueOf(event.get("targetUserId"))));
    }
}
