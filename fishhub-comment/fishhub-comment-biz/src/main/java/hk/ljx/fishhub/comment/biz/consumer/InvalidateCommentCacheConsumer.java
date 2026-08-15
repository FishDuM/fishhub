package hk.ljx.fishhub.comment.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.cache.CommentDetailCache;
import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.comment.biz.model.dto.InvalidateCommentCacheMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 消费删除事实提交后的远端缓存失效事件。Redis 失败时抛出异常，由 RocketMQ 重试。
 */
@Component
@Slf4j
@RocketMQMessageListener(
        consumerGroup = "fishhub_group_" + MQConstants.TOPIC_INVALIDATE_COMMENT_CACHE,
        topic = MQConstants.TOPIC_INVALIDATE_COMMENT_CACHE)
public class InvalidateCommentCacheConsumer implements RocketMQListener<String> {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private CommentDetailCache commentDetailCache;

    @Override
    public void onMessage(String body) {
        InvalidateCommentCacheMqDTO task = JsonUtils.parseObject(body, InvalidateCommentCacheMqDTO.class);
        if (task == null || StrUtil.isBlank(task.getEventId()) || CollUtil.isEmpty(task.getDeletedCommentIds())) {
            throw new IllegalArgumentException("评论缓存失效消息缺少必要字段");
        }

        List<Long> commentIds = task.getDeletedCommentIds().stream().distinct().toList();
        List<String> countKeys = new ArrayList<>(commentIds.stream()
                .map(RedisKeyConstants::buildCountCommentKey)
                .toList());
        List<String> detailKeys = new ArrayList<>(commentIds.stream()
                .map(RedisKeyConstants::buildCommentDetailKey)
                .toList());
        if (task.getParentCommentId() != null) {
            countKeys.add(RedisKeyConstants.buildCountCommentKey(task.getParentCommentId()));
            detailKeys.add(RedisKeyConstants.buildCommentDetailKey(task.getParentCommentId()));
            redisTemplate.delete(RedisKeyConstants.buildHaveFirstReplyCommentKey(task.getParentCommentId()));
        }
        redisTemplate.delete(countKeys);
        commentDetailCache.delete(detailKeys);
        log.info("评论缓存已失效，eventId={}, deletedCommentCount={}",
                task.getEventId(), commentIds.size());
    }
}
