package hk.ljx.fishhub.comment.biz.service;

import cn.hutool.core.collection.CollUtil;
import hk.ljx.framework.common.util.CacheTtl;
import hk.ljx.fishhub.comment.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentDO;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentLikeDO;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentLikeDOMapper;
import hk.ljx.fishhub.comment.biz.enums.CommentLevelEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
public class CommentLikeRealtimeService {

    private static final long BASE_EXPIRE_SECONDS = 5 * 60 * 60L;

    private static DefaultRedisScript<Long> luaScript(String luaPath) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(luaPath)));
        script.setResultType(Long.class);
        return script;
    }

    /**
     * 点赞/取消原子脚本：根据成员变动原子更新计数与足迹
     */
    private static final DefaultRedisScript<Long> LIKE_TOGGLE_SCRIPT = luaScript("/lua/comment_like_toggle.lua");

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CommentDOMapper commentDOMapper;
    @Resource
    private CommentLikeDOMapper commentLikeDOMapper;

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
        long total = sorted.size();
        int from = (pageNo - 1) * pageSize;
        if (from >= sorted.size()) {
            return new LikedCommentPage(List.of(), total);
        }
        int to = Math.min(from + pageSize, sorted.size());
        List<Long> ids = sorted.subList(from, to).stream()
                .map(CommentLikeDO::getCommentId)
                .toList();
        return new LikedCommentPage(ids, total);
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

    /**
     * 冷缓存重建：Set（哨兵 + 全部已赞评论 ID）+ ZSet（score=点赞时间）一次性回源。
     */
    private void rebuildFromDatabase(String setKey, String zsetKey, List<CommentLikeDO> allLikes) {
        try {
            stringRedisTemplate.opsForSet().add(setKey, RedisKeyConstants.COMMENT_LIKE_SET_INITIALIZED);
            if (CollUtil.isNotEmpty(allLikes)) {
                List<String> members = allLikes.stream()
                        .map(CommentLikeDO::getCommentId)
                        .filter(Objects::nonNull)
                        .map(String::valueOf)
                        .toList();
                if (!members.isEmpty()) {
                    stringRedisTemplate.opsForSet().add(setKey, members.toArray(String[]::new));
                }
                for (CommentLikeDO like : allLikes) {
                    if (like.getCommentId() == null) {
                        continue;
                    }
                    double score = like.getCreateTime() == null ? 0D
                            : like.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                    stringRedisTemplate.opsForZSet().add(
                            zsetKey, String.valueOf(like.getCommentId()), score);
                }
            }
            stringRedisTemplate.expire(setKey, BASE_EXPIRE_SECONDS, TimeUnit.SECONDS);
            stringRedisTemplate.expire(zsetKey, BASE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis 不可用，点赞缓存重建失败（下次读会回源数据库）, setKey={}", setKey, e);
        }
    }

    private void executeToggle(Long userId, Long commentId, int incr) {
        try {
            stringRedisTemplate.execute(LIKE_TOGGLE_SCRIPT,
                    List.of(RedisKeyConstants.buildCountCommentKey(commentId),
                            RedisKeyConstants.buildUserCommentLikeSetKey(userId),
                            RedisKeyConstants.buildUserCommentLikeZSetKey(userId)),
                    String.valueOf(commentId),
                    String.valueOf(incr),
                    String.valueOf(CacheTtl.hours(0, 5)),
                    String.valueOf(System.currentTimeMillis()));
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
        String key = RedisKeyConstants.buildCountCommentKey(commentId);
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
            fields.put(RedisKeyConstants.FIELD_LIKE_TOTAL,
                    String.valueOf(comment.getLikeTotal() == null ? 0L : comment.getLikeTotal()));
            if (Objects.equals(comment.getLevel(), CommentLevelEnum.ONE.getCode())) {
                fields.put(RedisKeyConstants.FIELD_CHILD_COMMENT_TOTAL,
                        String.valueOf(comment.getChildCommentTotal() == null ? 0L : comment.getChildCommentTotal()));
            }
            long expireSeconds = CacheTtl.hours(0, 5);
            stringRedisTemplate.opsForHash().putAll(key, fields);
            stringRedisTemplate.expire(key, expireSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("评论计数基线初始化失败, commentId={}", commentId, e);
        }
    }
}
