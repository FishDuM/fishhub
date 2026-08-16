package hk.ljx.fishhub.comment.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.cache.CommentDetailCache;
import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.comment.biz.enums.CommentLevelEnum;
import hk.ljx.fishhub.comment.biz.model.dto.CommentChangedEventMqDTO;
import hk.ljx.fishhub.comment.biz.model.dto.CommentItemMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 消费评论变更事件，失效 Redis 中的评论列表与详情缓存。
 * 发布：一级评论所在笔记的一级评论列表 + 二级评论所属父评论的子列表；
 * 删除：上述列表 + 被删评论与其父评论的详情/计数缓存。重复删除安全。
 */
@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_COMMENT_CHANGED + "_cache",
        topic = MQConstants.TOPIC_COMMENT_CHANGED)
public class CommentChangedCacheInvalidateConsumer implements RocketMQListener<String> {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private CommentDetailCache commentDetailCache;

    @Override
    public void onMessage(String body) {
        CommentChangedEventMqDTO event = JsonUtils.parseObject(body, CommentChangedEventMqDTO.class);
        if (event == null || event.getChangeType() == null || CollUtil.isEmpty(event.getItems())) {
            throw new IllegalArgumentException("评论缓存失效消息缺少必要字段");
        }

        boolean isDelete = Objects.equals(event.getChangeType(), MQConstants.COMMENT_CHANGE_TYPE_DELETE);

        // 一级评论所在笔记的一级评论列表缓存（发布与删除都需要失效）
        event.getItems().stream()
                .filter(item -> Objects.equals(item.getLevel(), CommentLevelEnum.ONE.getCode()))
                .map(CommentItemMqDTO::getNoteId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(this::invalidateOneLevelList);

        // 二级评论所属父评论的子列表缓存
        event.getItems().stream()
                .filter(item -> Objects.equals(item.getLevel(), CommentLevelEnum.TWO.getCode()))
                .map(CommentItemMqDTO::getParentId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(parentId ->
                        redisTemplate.delete(RedisKeyConstants.buildChildCommentListKey(parentId)));

        if (!isDelete) {
            return;
        }

        // 删除：失效被删评论与二级根评论父评论的详情/计数缓存
        List<Long> commentIds = event.getItems().stream()
                .map(CommentItemMqDTO::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<String> countKeys = new ArrayList<>(commentIds.stream()
                .map(RedisKeyConstants::buildCountCommentKey)
                .toList());
        List<String> detailKeys = new ArrayList<>(commentIds.stream()
                .map(RedisKeyConstants::buildCommentDetailKey)
                .toList());
        event.getItems().stream()
                .filter(item -> Objects.equals(item.getLevel(), CommentLevelEnum.TWO.getCode()))
                .map(CommentItemMqDTO::getParentId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(parentId -> {
                    countKeys.add(RedisKeyConstants.buildCountCommentKey(parentId));
                    detailKeys.add(RedisKeyConstants.buildCommentDetailKey(parentId));
                    redisTemplate.delete(RedisKeyConstants.buildHaveFirstReplyCommentKey(parentId));
                });
        redisTemplate.delete(countKeys);
        commentDetailCache.delete(detailKeys);
        log.info("评论删除后的缓存已失效, deletedCommentCount={}", commentIds.size());
    }

    /**
     * 一级评论列表缓存按版本键失效；新评论可能进入热点 ZSet，直接删除使下次读取以 MySQL 重建。
     */
    private void invalidateOneLevelList(Long noteId) {
        String versionKey = RedisKeyConstants.buildOneLevelCommentTotalCacheVersionKey(noteId);
        Long version = redisTemplate.opsForValue().increment(versionKey);
        if (version != null && version > 0) {
            redisTemplate.expire(versionKey,
                    RedisKeyConstants.ONE_LEVEL_COMMENT_TOTAL_CACHE_VERSION_EXPIRE_SECONDS, TimeUnit.SECONDS);
            redisTemplate.delete(RedisKeyConstants.buildOneLevelCommentTotalCacheKey(noteId,
                    String.valueOf(version - 1)));
        }
        redisTemplate.delete(List.of(RedisKeyConstants.buildCommentListKey(noteId)));
    }
}
