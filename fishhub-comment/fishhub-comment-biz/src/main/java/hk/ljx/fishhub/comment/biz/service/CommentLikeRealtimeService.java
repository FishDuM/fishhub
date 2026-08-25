package hk.ljx.fishhub.comment.biz.service;

import cn.hutool.core.collection.CollUtil;
import hk.ljx.framework.common.util.CacheTtl;
import hk.ljx.fishhub.comment.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.count.constant.CountKeyConstants;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentLikeDO;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentLikeDOMapper;
import hk.ljx.fishhub.comment.biz.enums.CommentLevelEnum;
import hk.ljx.framework.common.util.RedisScriptHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import hk.ljx.framework.common.util.DateUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 评论点赞实时交互层
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentLikeRealtimeService {

    private static final long FOOTPRINT_TTL_SECONDS = 7 * 86400L;
    private static final long COUNT_TTL_SECONDS = 30 * 86400L;
    private static final long EMPTY_BASELINE_TTL_SECONDS = 60L;

    /**
     * 点赞/取消原子脚本：根据成员变动原子更新计数与足迹
     */
    private static final DefaultRedisScript<Long> LIKE_TOGGLE_SCRIPT = RedisScriptHelper.loadLongScript("/lua/comment_like_toggle.lua");

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
     *
     * @param noteId 笔记 ID
     * @param commentId 一级评论 ID
     * @param delta 热度增量（如点赞 +1.0，取消点赞 -1.0，子回复 +2.0，删除回复 -2.0）
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
                    RedisKeyConstants.buildUserCommentLikeSetKey(userId),
                    RedisKeyConstants.buildUserCommentLikeZSetKey(userId),
                    CountKeyConstants.buildCountCommentKey(commentId)));
        } catch (Exception e) {
            log.warn("清空评论点赞缓存失败, userId={}, commentId={}", userId, commentId, e);
        }
    }

    /**
     * 批量过滤已点赞评论 ID
     */
    public List<Long> filterLikedCommentIds(Long userId, List<Long> commentIds) {
        if (CollUtil.isEmpty(commentIds)) {
            return List.of();
        }
        String setKey = RedisKeyConstants.buildUserCommentLikeSetKey(userId);
        if (isSetInitialized(setKey)) {
            Object[] members = commentIds.stream().map(String::valueOf).toArray();
            Map<Object, Boolean> memberMap = stringRedisTemplate.opsForSet().isMember(setKey, members);
            if (CollUtil.isEmpty(memberMap)) {
                return List.of();
            }
            return commentIds.stream()
                    .filter(id -> Boolean.TRUE.equals(memberMap.get(String.valueOf(id))))
                    .toList();
        }
        // 冷缓存回源重建
        List<CommentLikeDO> allLikes = commentLikeDOMapper.selectLikedCommentsByUserId(userId);
        rebuildFromDatabase(setKey, RedisKeyConstants.buildUserCommentLikeZSetKey(userId), allLikes);
        Set<String> likedIds = allLikes.stream()
                .map(CommentLikeDO::getCommentId)
                .map(String::valueOf)
                .collect(Collectors.toSet());
        return commentIds.stream()
                .filter(commentId -> likedIds.contains(String.valueOf(commentId)))
                .toList();
    }

    /**
     * 判断用户是否已点赞该评论
     */
    public boolean containsLiked(Long userId, Long commentId) {
        try {
            String setKey = RedisKeyConstants.buildUserCommentLikeSetKey(userId);
            if (!isSetInitialized(setKey)) {
                rebuildFromDatabase(setKey, RedisKeyConstants.buildUserCommentLikeZSetKey(userId),
                        commentLikeDOMapper.selectLikedCommentsByUserId(userId));
            }
            return Boolean.TRUE.equals(stringRedisTemplate.opsForSet()
                    .isMember(setKey, String.valueOf(commentId)));
        } catch (Exception e) {
            log.warn("点赞状态判断降级走数据库, userId={}, commentId={}", userId, commentId, e);
            return commentLikeDOMapper.selectCountByUserIdAndCommentId(userId, commentId) > 0;
        }
    }

    /**
     * 我的点赞足迹分页
     */
    public LikedCommentPage pageLikedCommentIds(Long userId, int pageNo, int pageSize) {
        String setKey = RedisKeyConstants.buildUserCommentLikeSetKey(userId);
        String zsetKey = RedisKeyConstants.buildUserCommentLikeZSetKey(userId);
        if (isSetInitialized(setKey) && Boolean.TRUE.equals(stringRedisTemplate.hasKey(zsetKey))) {
            Long total = stringRedisTemplate.opsForZSet().zCard(zsetKey);
            long count = total == null ? 0L : total;
            long start = (long) (pageNo - 1) * pageSize;
            long end = start + pageSize - 1;
            Set<String> members = stringRedisTemplate.opsForZSet().reverseRange(zsetKey, start, end);
            List<Long> ids = members == null ? List.of() : members.stream()
                    .map(Long::valueOf)
                    .toList();
            return new LikedCommentPage(ids, count);
        }
        // 冷缓存：回源数据库重建，内存分页
        List<CommentLikeDO> allLikes = commentLikeDOMapper.selectLikedCommentsByUserId(userId);
        rebuildFromDatabase(setKey, zsetKey, allLikes);

        List<CommentLikeDO> sorted = allLikes.stream()
                .sorted(Comparator.comparing(CommentLikeDO::getCreateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<Long> ids = CollUtil.page(Math.max(0, pageNo - 1), pageSize, sorted).stream()
                .map(CommentLikeDO::getCommentId)
                .toList();
        return new LikedCommentPage(ids, sorted.size());
    }

    /** 我的点赞足迹分页结果 */
    public record LikedCommentPage(List<Long> commentIds, long total) {
    }

    private boolean isSetInitialized(String setKey) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.opsForSet()
                    .isMember(setKey, RedisKeyConstants.COMMENT_LIKE_SET_INITIALIZED));
        } catch (Exception e) {
            log.warn("Redis 不可用，已赞集合判断退化走数据库, key={}", setKey, e);
            return false;
        }
    }

    private void rebuildFromDatabase(String setKey, String zsetKey, List<CommentLikeDO> allLikes) {
        try {
            stringRedisTemplate.opsForSet().add(setKey, RedisKeyConstants.COMMENT_LIKE_SET_INITIALIZED);
            if (CollUtil.isNotEmpty(allLikes)) {
                List<String> members = new ArrayList<>(allLikes.size());
                Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>(allLikes.size());
                for (CommentLikeDO like : allLikes) {
                    if (like != null && like.getCommentId() != null) {
                        String member = String.valueOf(like.getCommentId());
                        members.add(member);
                        double score = like.getCreateTime() != null ? DateUtils.localDateTime2Timestamp(like.getCreateTime()) : 0.0;
                        tuples.add(new DefaultTypedTuple<>(member, score));
                    }
                }
                if (!members.isEmpty()) {
                    stringRedisTemplate.opsForSet().add(setKey, members.toArray(String[]::new));
                    stringRedisTemplate.opsForZSet().add(zsetKey, tuples);
                }
            }
            stringRedisTemplate.expire(setKey, FOOTPRINT_TTL_SECONDS, TimeUnit.SECONDS);
            stringRedisTemplate.expire(zsetKey, FOOTPRINT_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis 不可用，点赞缓存重建失败, setKey={}", setKey, e);
        }
    }

    private void executeToggle(Long userId, Long commentId, int incr) {
        try {
            stringRedisTemplate.execute(LIKE_TOGGLE_SCRIPT,
                    List.of(CountKeyConstants.buildCountCommentKey(commentId),
                            RedisKeyConstants.buildUserCommentLikeSetKey(userId),
                            RedisKeyConstants.buildUserCommentLikeZSetKey(userId)),
                    String.valueOf(commentId),
                    String.valueOf(incr),
                    String.valueOf(CacheTtl.hours(1, 4)),
                    String.valueOf(System.currentTimeMillis()),
                    String.valueOf(FOOTPRINT_TTL_SECONDS));
        } catch (Exception e) {
            // 实时层尽力而为：Redis 故障时回退到 MySQL 异步落盘，读侧在缓存 miss 时回源数据库
            log.warn("点赞实时更新失败（Redis 不可用？），将依赖 MySQL 落盘兜底, userId={}, commentId={}, incr={}",
                    userId, commentId, incr, e);
        }
    }

    /**
     * 若 count:comment 缓存未初始化，先回源数据库写入基线值，避免冷点赞把计数从 1 起跳（丢失历史基数）。
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
