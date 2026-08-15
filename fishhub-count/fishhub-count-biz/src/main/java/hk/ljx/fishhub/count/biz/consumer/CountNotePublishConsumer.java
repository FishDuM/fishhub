package hk.ljx.fishhub.count.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.count.biz.constant.MQConstants;
import hk.ljx.fishhub.count.biz.domain.mapper.UserCountDOMapper;
import hk.ljx.fishhub.count.biz.model.dto.NoteOperateMqDTO;
import hk.ljx.fishhub.count.biz.service.UserCountCacheVersionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Objects;


@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_NOTE_OPERATE, // Group 组
        topic = MQConstants.TOPIC_NOTE_OPERATE // 主题 Topic
        )
@Slf4j
public class CountNotePublishConsumer implements RocketMQListener<Message> {

    @Resource
    private UserCountDOMapper userCountDOMapper;
    @Resource
    private hk.ljx.fishhub.count.biz.service.MqIdempotentExecutor mqIdempotentExecutor;
    @Resource
    private UserCountCacheVersionService userCountCacheVersionService;

    @Override
    public void onMessage(Message message) {
        // 消息体
        String bodyJsonStr = new String(message.getBody());
        // 标签
        String tags = message.getTags();

        log.info("==> CountNotePublishConsumer 消费了消息 {}, tags: {}", bodyJsonStr, tags);

        // 根据 MQ 标签，判断笔记操作类型
        if (Objects.equals(tags, MQConstants.TAG_NOTE_PUBLISH)) { // 笔记发布
            handleTagMessage(bodyJsonStr, 1);
        } else if (Objects.equals(tags, MQConstants.TAG_NOTE_DELETE)) { // 笔记删除
            handleTagMessage(bodyJsonStr, -1);
        }
    }

    /**
     * 笔记发布、删除
     * @param bodyJsonStr
     */
    private void handleTagMessage(String bodyJsonStr, long count) {
        // 消息体 JSON 字符串转 DTO
        NoteOperateMqDTO noteOperateMqDTO = JsonUtils.parseObject(bodyJsonStr, NoteOperateMqDTO.class);

        if (Objects.isNull(noteOperateMqDTO) || noteOperateMqDTO.getCreatorId() == null) {
            throw new IllegalArgumentException("笔记发布计数消息缺少必要字段");
        }

        // 笔记发布者 ID
        Long creatorId = noteOperateMqDTO.getCreatorId();

        mqIdempotentExecutor.execute("count-note-publish", tagsIdentity(count, bodyJsonStr),
                () -> userCountDOMapper.insertOrUpdateNoteTotalByUserId(count, creatorId));
        userCountCacheVersionService.advanceVersion(creatorId);
    }

    private String tagsIdentity(long count, String body) {
        return count + ":" + body;
    }

}
