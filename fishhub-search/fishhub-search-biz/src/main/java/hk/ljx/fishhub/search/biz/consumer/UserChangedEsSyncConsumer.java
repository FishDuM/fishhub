package hk.ljx.fishhub.search.biz.consumer;

import hk.ljx.framework.common.constant.MqTopicConstants;
import hk.ljx.fishhub.search.biz.domain.mapper.SelectMapper;
import hk.ljx.fishhub.search.biz.index.NoteIndex;
import hk.ljx.fishhub.search.biz.service.EsIndexSyncAggregator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 用户资料变更事件 → 用户索引 + 该用户全部笔记索引同步（笔记文档内嵌昵称/头像）。
 */
@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_search_es_user_changed",
        topic = MqTopicConstants.TOPIC_USER_CHANGED)
@Slf4j
@RequiredArgsConstructor
public class UserChangedEsSyncConsumer implements RocketMQListener<String> {

    private final EsIndexSyncAggregator esIndexSyncAggregator;
    private final SelectMapper selectMapper;

    @Override
    public void onMessage(String body) {
        if (StringUtils.isBlank(body)) {
            return;
        }
        Long userId = Long.valueOf(body.trim());
        esIndexSyncAggregator.submitUser(userId);

        // 笔记文档冗余了用户昵称/头像，资料变更需重建该用户全部笔记文档
        List<Map<String, Object>> notes = selectMapper.selectEsNoteIndexData(null, userId);
        if (notes != null && !notes.isEmpty()) {
            esIndexSyncAggregator.submitNoteIds(notes.stream()
                    .map(row -> Long.valueOf(String.valueOf(row.get(NoteIndex.FIELD_NOTE_ID))))
                    .toList());
        }
    }
}
