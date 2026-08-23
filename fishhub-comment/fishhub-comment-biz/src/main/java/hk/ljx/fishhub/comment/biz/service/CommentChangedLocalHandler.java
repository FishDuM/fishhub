package hk.ljx.fishhub.comment.biz.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.google.common.collect.Sets;
import hk.ljx.framework.common.util.DateUtils;
import hk.ljx.fishhub.comment.biz.cache.CommentDetailCache;
import hk.ljx.fishhub.comment.biz.constant.MQConstants;
import hk.ljx.fishhub.comment.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.enums.CommentLevelEnum;
import hk.ljx.fishhub.comment.biz.model.bo.CommentFirstReplyBO;
import hk.ljx.fishhub.count.constant.CountKeyConstants;
import hk.ljx.fishhub.count.dto.CommentChangedEventMqDTO;
import hk.ljx.fishhub.count.dto.CommentItemMqDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 评论变更的模块内本地动作：列表缓存维护、热度聚合、首条回复回填。
 * 由发布/删除落库消费者在事务提交后同步执行，替代原先三个订阅 COMMENT_CHANGED 的广播消费者。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CommentChangedLocalHandler {

    private static final long COMMENT_LIST_MAX_SIZE = 500;
    /** 子评论缓存上限：重建与增量 trim 同向保留最新 N 条 */
    public static final long CHILD_COMMENT_LIST_MAX_SIZE = 5000;
    private static final long COMMENT_LIST_EXPIRE_SECONDS = 5 * 3600L;
    private static final long CHILD_COMMENT_LIST_EXPIRE_SECONDS = 5 * 3600L;

    private final StringRedisTemplate stringRedisTemplate;
    private final CommentDetailCache commentDetailCache;
    private final CommentDOMapper commentDOMapper;
    private final CommentHeatAggregator commentHeatAggregator;

    /**
     * 评论发布落库后调用。
     */
    public void handlePublish(CommentChangedEventMqDTO event) {
        handleCacheInvalidation(event, false);
        submitHeat(event);
        handleFirstReply(event);
    }

    /**
     * 评论删除落库后调用。
     */
    public void handleDelete(CommentChangedEventMqDTO event) {
        handleCacheInvalidation(event, true);
        submitHeat(event);
    }

    // —— 列表/详情缓存维护（原 CommentChangedCacheInvalidateConsumer）——

    private void handleCacheInvalidation(CommentChangedEventMqDTO event, boolean isDelete) {
        if (event == null || event.getChangeType() == null || CollUtil.isEmpty(event.getItems())) {
            throw new IllegalArgumentException("评论缓存失效消息缺少必要字段");
        }

        // 一级评论：维护笔记评论列表 ZSET + 一级评论计数版本
        Map<Long, List<Long>> oneLevelByNote = new HashMap<>();
        for (CommentItemMqDTO item : event.getItems()) {
            if (Objects.equals(item.getLevel(), CommentLevelEnum.ONE.getCode())
                    && item.getNoteId() != null && item.getId() != null) {
                oneLevelByNote.computeIfAbsent(item.getNoteId(), k -> new ArrayList<>()).add(item.getId());
            }
        }
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

    // —— 热度聚合（原 CommentChangedHeatConsumer）——

    private void submitHeat(CommentChangedEventMqDTO event) {
        // 二级评论的变动会影响其父评论的热度
        Set<Long> commentIds = Sets.newHashSet();
        event.getItems().stream()
                .filter(item -> Objects.equals(item.getLevel(), CommentLevelEnum.TWO.getCode()))
                .map(CommentItemMqDTO::getParentId)
                .filter(Objects::nonNull)
                .forEach(commentIds::add);
        commentHeatAggregator.submit(commentIds);
    }

    // —— 首条回复回填（原 CommentChangedFirstReplyUpdateConsumer）——

    private void handleFirstReply(CommentChangedEventMqDTO event) {
        List<Long> parentIds = event.getItems().stream()
                .filter(item -> Objects.equals(item.getLevel(), CommentLevelEnum.TWO.getCode()))
                .map(CommentItemMqDTO::getParentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (CollUtil.isEmpty(parentIds)) {
            return;
        }

        // 过滤 Redis 中已标记拥有首条回复的一级评论
        List<String> keys = parentIds.stream()
                .map(RedisKeyConstants::buildHaveFirstReplyCommentKey)
                .toList();
        List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
        List<Long> missingCommentIds = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            if (Objects.isNull(values.get(i))) {
                missingCommentIds.add(parentIds.get(i));
            }
        }
        if (CollUtil.isEmpty(missingCommentIds)) {
            return;
        }

        List<CommentDO> commentDOS = commentDOMapper.selectByCommentIds(missingCommentIds);

        List<Long> alreadyHasReplyIds = new ArrayList<>();
        List<Long> needUpdateCommentIds = new ArrayList<>();
        for (CommentDO commentDO : commentDOS) {
            Long replyId = commentDO.getFirstReplyCommentId();
            if (replyId != null && replyId > 0) {
                alreadyHasReplyIds.add(commentDO.getId());
            } else {
                needUpdateCommentIds.add(commentDO.getId());
            }
        }

        if (CollUtil.isNotEmpty(alreadyHasReplyIds)) {
            sync2Redis(alreadyHasReplyIds);
        }

        if (CollUtil.isEmpty(needUpdateCommentIds)) {
            return;
        }

        List<CommentDO> earliestReplies = commentDOMapper.selectEarliestFirstReplyByParentIds(needUpdateCommentIds);
        if (CollUtil.isEmpty(earliestReplies)) {
            return;
        }

        List<CommentFirstReplyBO> replyBOS = earliestReplies.stream()
                .map(reply -> CommentFirstReplyBO.builder()
                        .id(reply.getParentId())
                        .firstReplyCommentId(reply.getId())
                        .build())
                .toList();
        commentDOMapper.batchUpdateFirstReplyCommentIds(replyBOS);

        sync2Redis(replyBOS.stream().map(CommentFirstReplyBO::getId).toList());
    }

    /**
     * 同步 haveFirstReply 标记并失效该评论的详情缓存。
     */
    private void sync2Redis(List<Long> needSyncCommentIds) {
        try {
            needSyncCommentIds.forEach(commentId -> {
                stringRedisTemplate.opsForValue().set(
                        RedisKeyConstants.buildHaveFirstReplyCommentKey(commentId),
                        "1",
                        RandomUtil.randomInt(1, 5 * 60 * 60),
                        TimeUnit.SECONDS);
                stringRedisTemplate.delete(RedisKeyConstants.buildCommentDetailKey(commentId));
            });
        } catch (Exception e) {
            log.warn("Redis 不可用，评论首回复缓存同步失败", e);
        }
    }
}
