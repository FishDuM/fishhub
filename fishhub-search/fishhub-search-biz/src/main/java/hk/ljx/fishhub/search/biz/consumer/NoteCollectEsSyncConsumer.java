package hk.ljx.fishhub.search.biz.consumer;

import hk.ljx.framework.common.constant.MqTopicConstants;
import hk.ljx.fishhub.search.biz.service.EsIndexSyncAggregator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 笔记收藏计数事件 → 笔记/作者索引计数同步。
 */
@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_search_es_note_collect",
        topic = MqTopicConstants.TOPIC_COUNT_NOTE_COLLECT)
@Slf4j
@RequiredArgsConstructor
public class NoteCollectEsSyncConsumer implements RocketMQListener<String> {

    private final EsIndexSyncAggregator esIndexSyncAggregator;

    @Override
    public void onMessage(String body) {
        List<Map<String, Object>> events = NoteLikeEsSyncConsumer.parseCountEvents(body);
        for (Map<String, Object> event : events) {
            Long noteId = Long.valueOf(String.valueOf(event.get("noteId")));
            esIndexSyncAggregator.submitNote(noteId);
            Object creatorId = event.get("noteCreatorId");
            if (creatorId != null) {
                esIndexSyncAggregator.submitUser(Long.valueOf(String.valueOf(creatorId)));
            }
        }
    }
}
