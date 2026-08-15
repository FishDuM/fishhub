package hk.ljx.fishhub.note.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.note.biz.constant.MQConstants;
import hk.ljx.fishhub.note.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.note.biz.model.dto.NoteOperateMqDTO;
import jakarta.annotation.Resource;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RocketMQMessageListener(
        consumerGroup = "fishhub_group_" + MQConstants.TOPIC_INVALIDATE_NOTE_REDIS_CACHE,
        topic = MQConstants.TOPIC_INVALIDATE_NOTE_REDIS_CACHE)
public class InvalidateNoteRedisCacheConsumer implements RocketMQListener<String> {

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Override
    public void onMessage(String body) {
        NoteOperateMqDTO event = JsonUtils.parseObject(body, NoteOperateMqDTO.class);
        if (event == null || event.getNoteId() == null || event.getCreatorId() == null) {
            throw new IllegalArgumentException("笔记缓存失效消息缺少必要字段");
        }
        redisTemplate.delete(List.of(
                RedisKeyConstants.buildNoteDetailKey(event.getNoteId()),
                RedisKeyConstants.buildNoteAccessKey(event.getNoteId()),
                RedisKeyConstants.buildPublishedNoteListKey(event.getCreatorId()),
                // 删除版本标记后，下一次发现页请求会生成新版本，旧页缓存自然失效。
                RedisKeyConstants.discoverFeedVersionKey()));
    }
}
