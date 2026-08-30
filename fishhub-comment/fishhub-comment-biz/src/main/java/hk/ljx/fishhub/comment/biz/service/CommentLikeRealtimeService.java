package hk.ljx.fishhub.comment.biz.service;

import cn.hutool.core.collection.CollUtil;
import hk.ljx.framework.common.util.CacheTtl;
import hk.ljx.framework.common.util.DateUtils;
import hk.ljx.fishhub.comment.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.count.constant.CountKeyConstants;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentLikeDO;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentLikeDOMapper;
import hk.ljx.fishhub.comment.biz.enums.CommentLevelEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeUnit;

/**
 * 评论点赞实时交互层（基于纯 ZSet + Redis Pipeline 高性能管道实现）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentLikeRealtimeService {

    private static final String INITIALIZED_MEMBER = "__empty__";
    private static final long FOOTPRINT_TTL_SECONDS = 7 * 86400L;

    private final StringRedisTemplate stringRedisTemplate;
    private final CommentDOMapper commentDOMapper;
    private final CommentLikeDOMapper commentLikeDOMapper;

    /**
     * 点赞
     */
    public void markLiked(Long userId, Long commentId) {
        ensureCountBaseline(commentId);
        executeToggle(userId, commentId, 1);
    }

    /**
     * 取消点赞
     */
    public void markUnliked(Long userId, Long commentId) {
        ensureCountBaseline(commentId);
        executeToggle(userId, commentId, -1);
    }

    /**
     * 即时增减一级评论在 Redis ZSet 中的热度分值（毫秒级 0 延迟调分）
     */
    public void incrementCommentHeat(Long noteId, Long commentId, double delta) {
        if (noteId == null || commentId == null) {
            return;
        }
        try {
            String key = RedisKeyConstants.buildCommentListKey(noteId);
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                Double currentScore = stringRedisTemplate.opsForZSet().score(key, String.valueOf(commentId));
                if (currentScore != null) {
                    double newScore = Math.max(0.0, currentScore + delta);
                    stringRedisTemplate.opsForZSet().add(key, String.valueOf(commentId), newScore);
                }
            }
        } catch (Exception e) {
            log.warn("Redis ZSet 评论热度即时调分异常, noteId={}, commentId={}, delta={}", noteId, commentId, delta, e);
        }
    }

    /**
     * 清空点赞状态与计数缓存。
     */
    public void evictLikeState(Long userId, Long commentId) {
        try {
            stringRedisTemplate.delete(Arrays.asList(
                    RedisKeyConstants.buildUserCommentLikeZSetKey(userId),
                    CountKeyConstants.buildCountCommentKey(commentId)));
        } catch (Exception e) {
            log.warn("清空评论点赞缓存失败, userId={}, commentId={}", userId, commentId, e);
        }
    }

    /**
     * 批量过滤已点赞评论 ID（Pipeline 1 次 RTT 批量查询）
     */
    public List<Long> filterLikedCommentIds(Long userId, List<Long> commentIds) {
        if (CollUtil.isEmpty(commentIds)) {
            return List.of();
        }
        String zsetKey = ensureZSetCache(userId);
        try {
            List<Object> results = stringRedisTemplate.executePipelined(new SessionCallback<>() {
                @Override
                public Object execute(RedisOperations operations) {
                    for (Long commentId : commentIds) {
                        operations.opsForZSet().score(zsetKey, String.valueOf(commentId));
                    }
                    return null;
                }
            });

            List<Long> likedIds = new ArrayList<>();
            for (int i = 0; i < commentIds.size(); i++) {
                if (i < results.size() && results.get(i) != null) {
                    likedIds.add(commentIds.get(i));
                }
            }
            return likedIds;
        } catch (Exception e) {
            log.warn("批量过滤已点赞评论 ID 异常，降级走数据库, userId={}", userId, e);
            return commentLikeDOMapper.selectLikedCommentIds(userId, commentIds);
        }
    }

    /**
     * 判断用户是否已点赞该评论（ZSCORE O(1) 判重）
     */
    public boolean containsLiked(Long userId, Long commentId) {
        if (userId == null || commentId == null) {
            return false;
        }
        try {
            String zsetKey = ensureZSetCache(userId);
            Double score = stringRedisTemplate.opsForZSet().score(zsetKey, String.valueOf(commentId));
            return score != null;
        } catch (Exception e) {
            log.warn("点赞状态判断降级走数据库, userId={}, commentId={}", userId, commentId, e);
            return commentLikeDOMapper.selectCountByUserIdAndCommentId(userId, commentId) > 0;
        }
    }

    /**
     * 我的点赞足迹分页
     */
    public LikedCommentPage pageLikedCommentIds(Long userId, int pageNo, int pageSize) {
        String zsetKey = ensureZSetCache(userId);
        try {
            Long total = stringRedisTemplate.opsForZSet().zCard(zsetKey);
            // 扣减防击穿哨兵占位
            long count = (total != null && total > 0) ? Math.max(0, total - 1) : 0L;
            long start = (long) (pageNo - 1) * pageSize;
            long end = start + pageSize - 1;
            Set<String> members = stringRedisTemplate.opsForZSet().reverseRange(zsetKey, start, end);
            List<Long> ids = (members == null ? List.<String>of() : members).stream()
                    .filter(m -> !INITIALIZED_MEMBER.equals(m))
                    .map(Long::valueOf)
                    .toList();
            return new LikedCommentPage(ids, count);
        } catch (Exception e) {
            log.warn("点赞足迹分页查询异常，降级走数据库, userId={}", userId, e);
            List<CommentLikeDO> allLikes = commentLikeDOMapper.selectLikedCommentsByUserId(userId);
            List<CommentLikeDO> sorted = allLikes.stream()
                    .sorted(Comparator.comparing(CommentLikeDO::getCreateTime,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
            List<Long> ids = CollUtil.page(Math.max(0, pageNo - 1), pageSize, sorted).stream()
                    .map(CommentLikeDO::getCommentId)
                    .toList();
            return new LikedCommentPage(ids, sorted.size());
        }
    }

    /** 我的点赞足迹分页结果 */
    public record LikedCommentPage(List<Long> commentIds, long total) {
    }

    private String ensureZSetCache(Long userId) {
        String zsetKey = RedisKeyConstants.buildUserCommentLikeZSetKey(userId);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(zsetKey))) {
            return zsetKey;
        }
        List<CommentLikeDO> allLikes = commentLikeDOMapper.selectLikedCommentsByUserId(userId);
        rebuildZSetFromDatabase(zsetKey, allLikes);
        return zsetKey;
    }

    private void rebuildZSetFromDatabase(String zsetKey, List<CommentLikeDO> allLikes) {
        try {
            // 写入哨兵元素，防止冷缓存击穿
            stringRedisTemplate.opsForZSet().add(zsetKey, INITIALIZED_MEMBER, 0.0);
            if (CollUtil.isNotEmpty(allLikes)) {
                Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>(allLikes.size());
                for (CommentLikeDO like : allLikes) {
                    if (like != null && like.getCommentId() != null) {
                        String member = String.valueOf(like.getCommentId());
                        double score = like.getCreateTime() != null ? DateUtils.localDateTime2Timestamp(like.getCreateTime()) : 0.0;
                        tuples.add(new DefaultTypedTuple<>(member, score));
                    }
                }
                stringRedisTemplate.opsForZSet().add(zsetKey, tuples);
            }
            stringRedisTemplate.expire(zsetKey, FOOTPRINT_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis 不可用，点赞缓存重建失败, zsetKey={}", zsetKey, e);
        }
    }

    /**
     * 纯 ZSet + Pipeline 打包：1 次网络往返同时更新 ZSet 足迹、点赞计数与 TTL
     */
    private void executeToggle(Long userId, Long commentId, int incr) {
        try {
            String countKey = CountKeyConstants.buildCountCommentKey(commentId);
            String zsetKey = RedisKeyConstants.buildUserCommentLikeZSetKey(userId);
            String commentIdStr = String.valueOf(commentId);

            stringRedisTemplate.executePipelined(new SessionCallback<>() {
                @Override
                public Object execute(RedisOperations operations) {
                    if (incr > 0) {
                        operations.opsForZSet().add(zsetKey, commentIdStr, System.currentTimeMillis());
                    } else {
                        operations.opsForZSet().remove(zsetKey, commentIdStr);
                    }
                    operations.opsForHash().increment(countKey, CountKeyConstants.FIELD_LIKE_TOTAL, incr);
                    operations.expire(zsetKey, FOOTPRINT_TTL_SECONDS, TimeUnit.SECONDS);
                    operations.expire(countKey, CacheTtl.hours(1, 4), TimeUnit.SECONDS);
                    return null;
                }
            });
        } catch (Exception e) {
            log.warn("点赞实时更新失败（Redis 不可用？），将依赖 MySQL 落盘兜底, userId={}, commentId={}, incr={}",
                    userId, commentId, incr, e);
        }
    }

    /**
     * 若 count:comment 缓存未初始化，先回源数据库写入基线值，避免冷点赞把计数从 1 起跳。
     */
    private void ensureCountBaseline(Long commentId) {
        String key = CountKeyConstants.buildCountCommentKey(commentId);
        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                return;
            }
            List<CommentDO> records = commentDOMapper.selectCommentCountByIds(List.of(commentId));
            if (CollUtil.isEmpty(records)) {
                return;
            }
            CommentDO comment = records.get(0);
            Map<String, String> fields = new HashMap<>();
            fields.put(CountKeyConstants.FIELD_LIKE_TOTAL,
                    String.valueOf(comment.getLikeTotal() == null ? 0L : comment.getLikeTotal()));
            if (Objects.equals(comment.getLevel(), CommentLevelEnum.ONE.getCode())) {
                fields.put(CountKeyConstants.FIELD_CHILD_COMMENT_TOTAL,
                        String.valueOf(comment.getChildCommentTotal() == null ? 0L : comment.getChildCommentTotal()));
            }
            long expireSeconds = CacheTtl.hours(1, 4);
            stringRedisTemplate.opsForHash().putAll(key, fields);
            stringRedisTemplate.expire(key, expireSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("评论计数基线初始化失败, commentId={}", commentId, e);
        }
    }
}
