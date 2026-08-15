package hk.ljx.fishhub.comment.biz.consumer;

import cn.hutool.core.util.StrUtil;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.comment.biz.model.dto.InvalidateOneLevelCommentCacheMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 消费评论事实提交后的缓存失效事件。Redis 失败时抛出异常，由 RocketMQ 重试；重复消费只会额外失效缓存。
 */
@Component
@Slf4j
@RocketMQMessageListener(
        consumerGroup = "fishhub_group_" + MQConstants.TOPIC_INVALIDATE_ONE_LEVEL_COMMENT_CACHE,
        topic = MQConstants.TOPIC_INVALIDATE_ONE_LEVEL_COMMENT_CACHE)
public class InvalidateOneLevelCommentCacheConsumer implements RocketMQListener<String> {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onMessage(String body) {
        InvalidateOneLevelCommentCacheMqDTO task = JsonUtils.parseObject(
                body, InvalidateOneLevelCommentCacheMqDTO.class);
        if (task == null || task.getNoteId() == null || StrUtil.isBlank(task.getEventId())) {
            throw new IllegalArgumentException("一级评论缓存失效消息缺少必要字段");
        }
        Long noteId = task.getNoteId();
        String versionKey = RedisKeyConstants.buildOneLevelCommentTotalCacheVersionKey(noteId);
        Long version = redisTemplate.opsForValue().increment(versionKey);
        if (version != null && version > 0) {
            redisTemplate.expire(versionKey,
                    RedisKeyConstants.ONE_LEVEL_COMMENT_TOTAL_CACHE_VERSION_EXPIRE_SECONDS, TimeUnit.SECONDS);
            redisTemplate.delete(RedisKeyConstants.buildOneLevelCommentTotalCacheKey(noteId,
                    String.valueOf(version - 1)));
        }
        // 新评论可能需要进入热点 ZSet；直接删除使下次读取以 MySQL 重建，避免消费者崩溃留下半更新列表。
        redisTemplate.delete(List.of(RedisKeyConstants.buildCommentListKey(noteId)));
        log.info("一级评论缓存已失效，eventId={}, noteId={}", task.getEventId(), noteId);
    }
}
