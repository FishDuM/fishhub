package hk.ljx.fishhub.search.biz.consumer;

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
 * 笔记点赞计数事件 → 笔记/作者索引计数同步。
 */
@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_search_es_note_like",
        topic = hk.ljx.framework.common.constant.MqTopicConstants.TOPIC_COUNT_NOTE_LIKE)
@Slf4j
@RequiredArgsConstructor
public class NoteLikeEsSyncConsumer implements RocketMQListener<String> {

    private final EsIndexSyncAggregator esIndexSyncAggregator;

    @Override
    public void onMessage(String body) {
        handleCountEvents(body);
    }

    void handleCountEvents(String body) {
        List<Map<String, Object>> events = parseCountEvents(body);
        for (Map<String, Object> event : events) {
            Long noteId = Long.valueOf(String.valueOf(event.get("noteId")));
            esIndexSyncAggregator.submitNote(noteId);
            Object creatorId = event.get("noteCreatorId");
            if (creatorId != null) {
                esIndexSyncAggregator.submitUser(Long.valueOf(String.valueOf(creatorId)));
            }
        }
    }

    static List<Map<String, Object>> parseCountEvents(String body) {
        String trimmed = body.trim();
        try {
            if (trimmed.startsWith("[")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> events = (List<Map<String, Object>>) (List<?>) JsonUtils.parseList(trimmed, Map.class);
                return events;
            }
            return List.of(JsonUtils.parseObject(trimmed, Map.class));
        } catch (Exception e) {
            throw new IllegalArgumentException("计数消息格式错误", e);
        }
    }
}
