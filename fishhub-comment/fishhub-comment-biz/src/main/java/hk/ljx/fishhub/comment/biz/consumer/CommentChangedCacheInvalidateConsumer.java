package hk.ljx.fishhub.comment.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import hk.ljx.framework.common.util.DateUtils;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.cache.CommentDetailCache;
import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.count.constant.CountKeyConstants;
import hk.ljx.fishhub.comment.biz.enums.CommentLevelEnum;
import hk.ljx.fishhub.count.dto.CommentChangedEventMqDTO;
import hk.ljx.fishhub.count.dto.CommentItemMqDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 评论变更缓存更新消费者
 */
@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_COMMENT_CHANGED + "_cache",
        topic = MQConstants.TOPIC_COMMENT_CHANGED)
@RequiredArgsConstructor
public class CommentChangedCacheInvalidateConsumer implements RocketMQListener<String> {

    private static final long COMMENT_LIST_MAX_SIZE = 500;
    private static final long CHILD_COMMENT_LIST_MAX_SIZE = 60;
    private static final long COMMENT_LIST_EXPIRE_SECONDS = 5 * 3600L;
    private static final long CHILD_COMMENT_LIST_EXPIRE_SECONDS = 5 * 3600L;

    private final StringRedisTemplate stringRedisTemplate;
    private final CommentDetailCache commentDetailCache;

    @Override
    public void onMessage(String body) {
        CommentChangedEventMqDTO event = JsonUtils.parseObject(body, CommentChangedEventMqDTO.class);
        if (event == null || event.getChangeType() == null || CollUtil.isEmpty(event.getItems())) {
            throw new IllegalArgumentException("评论缓存失效消息缺少必要字段");
        }

        boolean isDelete = Objects.equals(event.getChangeType(), MQConstants.COMMENT_CHANGE_TYPE_DELETE);

        // 一级评论：维护笔记评论列表 ZSET + 一级评论计数版本
        Map<Long, List<Long>> oneLevelByNote = event.getItems().stream()
                .filter(item -> Objects.equals(item.getLevel(), CommentLevelEnum.ONE.getCode()))
                .filter(item -> item.getNoteId() != null && item.getId() != null)
                .collect(Collectors.groupingBy(CommentItemMqDTO::getNoteId,
                        Collectors.mapping(CommentItemMqDTO::getId, Collectors.toList())));
        oneLevelByNote.forEach((noteId, commentIds) -> {
            if (isDelete) {
                deleteOneLevelComments(noteId, commentIds);
            } else {
                publishOneLevelComments(noteId, commentIds);
            }
        });

        // 二级评论：增量维护父评论的子列表 ZSET
        event.getItems().stream()
                .filter(item -> Objects.equals(item.getLevel(), CommentLevelEnum.TWO.getCode()))
                .filter(item -> item.getParentId() != null && item.getId() != null)
                .forEach(item -> {
                    if (isDelete) {
                        stringRedisTemplate.opsForZSet().remove(
                                RedisKeyConstants.buildChildCommentListKey(item.getParentId()),
                                String.valueOf(item.getId()));
                    } else {
                        publishChildComment(item);
                    }
                });

        if (!isDelete) {
            List<String> parentCountKeys = event.getItems().stream()
                    .filter(item -> Objects.equals(item.getLevel(), CommentLevelEnum.TWO.getCode()))
                    .map(CommentItemMqDTO::getParentId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .map(CountKeyConstants::buildCountCommentKey)
                    .toList();
            if (CollUtil.isNotEmpty(parentCountKeys)) {
                stringRedisTemplate.delete(parentCountKeys);
            }
            return;
        }

        // 删除：失效被删评论与二级根评论父评论的详情/计数缓存
        List<Long> commentIds = event.getItems().stream()
                .map(CommentItemMqDTO::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<String> countKeys = new ArrayList<>(commentIds.stream()
                .map(CountKeyConstants::buildCountCommentKey)
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
                    countKeys.add(CountKeyConstants.buildCountCommentKey(parentId));
                    detailKeys.add(RedisKeyConstants.buildCommentDetailKey(parentId));
                    stringRedisTemplate.delete(RedisKeyConstants.buildHaveFirstReplyCommentKey(parentId));
                });
        stringRedisTemplate.delete(countKeys);
        commentDetailCache.delete(detailKeys);
        log.info("评论删除后的缓存已失效, deletedCommentCount={}", commentIds.size());
    }

    private void publishOneLevelComments(Long noteId, List<Long> commentIds) {
        bumpOneLevelCommentTotalVersion(noteId);
        String key = RedisKeyConstants.buildCommentListKey(noteId);
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            return;
        }
        ZSetOperations<String, String> zSet = stringRedisTemplate.opsForZSet();
        commentIds.forEach(commentId -> zSet.add(key, String.valueOf(commentId), 0D));
        trimZSet(zSet, key, COMMENT_LIST_MAX_SIZE);
        stringRedisTemplate.expire(key, COMMENT_LIST_EXPIRE_SECONDS, TimeUnit.SECONDS);
    }

    private void deleteOneLevelComments(Long noteId, List<Long> commentIds) {
        bumpOneLevelCommentTotalVersion(noteId);
        String key = RedisKeyConstants.buildCommentListKey(noteId);
        ZSetOperations<String, String> zSet = stringRedisTemplate.opsForZSet();
        commentIds.forEach(commentId -> zSet.remove(key, String.valueOf(commentId)));
    }

    private void publishChildComment(CommentItemMqDTO item) {
        String key = RedisKeyConstants.buildChildCommentListKey(item.getParentId());
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            return;
        }
        ZSetOperations<String, String> zSet = stringRedisTemplate.opsForZSet();
        zSet.add(key, String.valueOf(item.getId()), DateUtils.localDateTime2Timestamp(item.getCreateTime()));
        trimZSet(zSet, key, CHILD_COMMENT_LIST_MAX_SIZE);
        stringRedisTemplate.expire(key, CHILD_COMMENT_LIST_EXPIRE_SECONDS, TimeUnit.SECONDS);
    }

    private void trimZSet(ZSetOperations<String, String> zSet, String key, long maxSize) {
        Long size = zSet.zCard(key);
        if (size != null && size > maxSize) {
            zSet.removeRange(key, 0, -(maxSize + 1));
        }
    }

    private void bumpOneLevelCommentTotalVersion(Long noteId) {
        String versionKey = RedisKeyConstants.buildOneLevelCommentTotalCacheVersionKey(noteId);
        Long version = stringRedisTemplate.opsForValue().increment(versionKey);
        if (version != null && version > 0) {
            stringRedisTemplate.expire(versionKey,
                    RedisKeyConstants.ONE_LEVEL_COMMENT_TOTAL_CACHE_VERSION_EXPIRE_SECONDS, TimeUnit.SECONDS);
            stringRedisTemplate.delete(RedisKeyConstants.buildOneLevelCommentTotalCacheKey(noteId,
                    String.valueOf(version - 1)));
        }
    }
}
