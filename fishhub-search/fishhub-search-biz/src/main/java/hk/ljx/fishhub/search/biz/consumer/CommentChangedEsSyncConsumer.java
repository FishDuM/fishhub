package hk.ljx.fishhub.search.biz.consumer;

import hk.ljx.framework.common.constant.MqTopicConstants;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.search.biz.service.EsIndexSyncAggregator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 评论变更事件 → 笔记评论计数同步。
 */
@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_search_es_comment_changed",
        topic = MqTopicConstants.TOPIC_COMMENT_CHANGED)
@Slf4j
@RequiredArgsConstructor
public class CommentChangedEsSyncConsumer implements RocketMQListener<String> {

    private final EsIndexSyncAggregator esIndexSyncAggregator;

    @Override
    public void onMessage(String body) {
        Map<?, ?> event = JsonUtils.parseObject(body, Map.class);
        if (event == null || event.get("items") == null) {
            throw new IllegalArgumentException("评论变更消息缺少 items");
        }
        Object items = event.get("items");
        if (!(items instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map && map.get("noteId") != null) {
                esIndexSyncAggregator.submitNote(Long.valueOf(String.valueOf(map.get("noteId"))));
            }
        }
    }
}
