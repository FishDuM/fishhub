package hk.ljx.fishhub.comment.biz.consumer;

import cn.hutool.core.util.StrUtil;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.comment.biz.model.dto.InvalidateChildCommentListCacheMqDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 二级评论列表是派生缓存；重复删除安全，Redis 故障由 RocketMQ 重试。
 */
@Component
@Slf4j
@RocketMQMessageListener(
        consumerGroup = "fishhub_group_" + MQConstants.TOPIC_INVALIDATE_CHILD_COMMENT_LIST_CACHE,
        topic = MQConstants.TOPIC_INVALIDATE_CHILD_COMMENT_LIST_CACHE)
public class InvalidateChildCommentListCacheConsumer implements RocketMQListener<String> {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onMessage(String body) {
        InvalidateChildCommentListCacheMqDTO task = JsonUtils.parseObject(
                body, InvalidateChildCommentListCacheMqDTO.class);
        if (task == null || task.getParentCommentId() == null || StrUtil.isBlank(task.getEventId())) {
            throw new IllegalArgumentException("二级评论列表缓存失效消息缺少必要字段");
        }
        redisTemplate.delete(RedisKeyConstants.buildChildCommentListKey(task.getParentCommentId()));
        log.info("二级评论列表缓存已失效，eventId={}, parentCommentId={}",
                task.getEventId(), task.getParentCommentId());
    }
}
