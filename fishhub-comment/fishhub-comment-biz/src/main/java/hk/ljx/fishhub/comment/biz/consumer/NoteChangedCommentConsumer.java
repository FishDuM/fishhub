package hk.ljx.fishhub.comment.biz.consumer;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.comment.biz.rpc.NoteRpcService;
import hk.ljx.fishhub.note.api.NoteChangedEventMqDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 监听笔记变更统一事件，级联清理评论缓存：
 * 笔记彻底删除时，立即释放该笔记下一级评论分页 ZSet、总数缓存与本地快照。
 */
@Component
@RocketMQMessageListener(
        consumerGroup = "fishhub_group_comment_" + MQConstants.TOPIC_NOTE_CHANGED,
        topic = MQConstants.TOPIC_NOTE_CHANGED
)
@Slf4j
@RequiredArgsConstructor
public class NoteChangedCommentConsumer implements RocketMQListener<String> {

    private static final int CHANGE_TYPE_DELETE = 0;

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void onMessage(String body) {
        if (StringUtils.isBlank(body)) {
            return;
        }

        NoteChangedEventMqDTO event = JsonUtils.parseObject(body, NoteChangedEventMqDTO.class);
        if (event == null || event.getNoteId() == null || event.getChangeType() == null) {
            return;
        }

        Long noteId = event.getNoteId();

        // 笔记被彻底删除时，级联清理评论相关 Redis 缓存
        if (Objects.equals(event.getChangeType(), CHANGE_TYPE_DELETE)) {
            NoteRpcService.invalidate(noteId);
            stringRedisTemplate.delete(List.of(
                    RedisKeyConstants.buildCommentListKey(noteId),
                    RedisKeyConstants.buildOneLevelCommentTotalCacheKey(noteId)
            ));
            log.info("评论服务收到笔记删除事件，已级联清理评论缓存, noteId={}", noteId);
        }
    }
}
