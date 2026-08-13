package hk.ljx.fishhub.count.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Lists;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.count.biz.constant.MQConstants;
import hk.ljx.fishhub.count.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.count.biz.domain.mapper.NoteCountDOMapper;
import hk.ljx.fishhub.count.biz.model.dto.CountPublishCommentMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_COUNT_NOTE_COMMENT, // Group 组
        topic = MQConstants.TOPIC_COUNT_NOTE_COMMENT // 主题 Topic
        )
@Slf4j
public class CountNoteCommentConsumer implements RocketMQListener<String> {

    @Resource
    private NoteCountDOMapper noteCountDOMapper;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private hk.ljx.fishhub.count.biz.service.MqIdempotentExecutor mqIdempotentExecutor;

    @Override
    public void onMessage(String body) {
        // 完成处理后才由 RocketMQ 确认消息，避免仅入内存队列即 ACK。
        consumeMessage(List.of(body));
    }

    private void consumeMessage(List<String> bodys) {
        log.info("==> 【笔记评论数】聚合消息, size: {}", bodys.size());
        log.info("==> 【笔记评论数】聚合消息, {}", JsonUtils.toJsonString(bodys));

        // 将聚合后的消息体 Json 转 List<CountPublishCommentMqDTO>
        List<CountPublishCommentMqDTO> countPublishCommentMqDTOList = Lists.newArrayList();
        bodys.forEach(body -> {
            try {
                List<CountPublishCommentMqDTO> list = JsonUtils.parseList(body, CountPublishCommentMqDTO.class);
                if (CollUtil.isEmpty(list) || list.stream().anyMatch(item -> item.getNoteId() == null
                        || item.getCommentId() == null || item.getLevel() == null)) {
                    throw new IllegalArgumentException("笔记评论计数消息缺少必要字段");
                }
                countPublishCommentMqDTOList.addAll(list);
            } catch (Exception e) {
                throw new IllegalArgumentException("笔记评论计数消息格式错误", e);
            }
        });

        // 按笔记 ID 进行分组
        Map<Long, List<CountPublishCommentMqDTO>> groupMap = countPublishCommentMqDTOList.stream()
                .collect(Collectors.groupingBy(CountPublishCommentMqDTO::getNoteId));

        String batchId = cn.hutool.crypto.digest.DigestUtil.sha256Hex(String.join("|", bodys));
        mqIdempotentExecutor.execute("count-note-comment", batchId, () ->
                groupMap.forEach((noteId, comments) -> {
                    int count = CollUtil.size(comments);
                    if (count > 0) {
                        noteCountDOMapper.insertOrUpdateCommentTotalByNoteId(count, noteId);
                    }
                }));
        redisTemplate.delete(groupMap.keySet().stream()
                .map(RedisKeyConstants::buildCountNoteKey)
                .toList());
    }
}
