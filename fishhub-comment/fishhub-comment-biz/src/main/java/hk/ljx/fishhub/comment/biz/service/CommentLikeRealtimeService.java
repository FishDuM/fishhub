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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 评论点赞实时链路（Redis 为展示真源，MySQL 异步落盘兜底）。
 *
 * <p>点赞/取消在请求侧同步更新三份 Redis 数据：
 * 1) 计数：{@code count:comment:{id}.likeTotal} 原子 HINCRBY ±1（取消时 clamp 不小于 0），并续 TTL；
 * 2) 已赞状态：{@code set:comment:likes:{userId}} SADD / SREM，读侧 {@code /comment/liked/ids} 优先走 Redis；
 * 3) 我的点赞足迹：{@code zset:comment:likes:{userId}} ZADD/ZREM（member=commentId，score=点赞时间），
 *    {@code /comment/liked/page} 用 ZREVRANGE 分页。
 * 冷缓存（无初始化哨兵）统一回源 t_comment_like 重建 Set + ZSet。
 *
 * <p>MySQL 侧由批量落库层（LikeUnlikeComment2DBConsumer）按 30 条一批、单事务写入，最终与 Redis 收敛。
 */
@Slf4j
@Service
public class CommentLikeRealtimeService {

    /** 评论计数缓存 TTL 基数（5 小时内随机抖动） */
    private static final long BASE_EXPIRE_SECONDS = 5 * 60 * 60L;

    /**
     * 点赞/取消原子脚本：计数与「集合成员是否真正变化」原子绑定。
     * SADD 返回 1（本轮真正新增）才 ZADD + 计数 +1；SREM 返回 1（真正取消）才 ZREM + 计数 -1（clamp≥0）。
     * → 重复点赞/连点/重复取消不会造成 Redis 计数漂移；与 2DB 侧按 affected 行数的 delta 语义一致。
     * KEYS[1]=countKey, KEYS[2]=userSetKey, KEYS[3]=userZSetKey；
     * ARGV[1]=commentId, ARGV[2]="1"/"-1", ARGV[3]=ttl, ARGV[4]=点赞时间戳(ms)
     */
    private static final DefaultRedisScript<Long> LIKE_TOGGLE_SCRIPT = new DefaultRedisScript<>(
            "if ARGV[2] == '1' then "
                    + "local added = redis.call('SADD', KEYS[2], ARGV[1]); "
                    + "if added == 1 then redis.call('ZADD', KEYS[3], ARGV[4], ARGV[1]); "
                    + "redis.call('HINCRBY', KEYS[1], 'likeTotal', 1); end; "
                    + "else "
                    + "local removed = redis.call('SREM', KEYS[2], ARGV[1]); "
                    + "if removed == 1 then redis.call('ZREM', KEYS[3], ARGV[1]); "
                    + "local v = redis.call('HINCRBY', KEYS[1], 'likeTotal', -1); "
                    + "if v < 0 then redis.call('HSET', KEYS[1], 'likeTotal', 0); end; "
                    + "end; "
                    + "end; "
                    + "redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3])); "
                    + "redis.call('EXPIRE', KEYS[2], tonumber(ARGV[3])); "
                    + "redis.call('EXPIRE', KEYS[3], tonumber(ARGV[3])); "
                    + "return 0", Long.class);

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CommentDOMapper commentDOMapper;
    @Resource
    private CommentLikeDOMapper commentLikeDOMapper;

    /**
     * 点赞实时更新：计数 +1、已赞集合 SADD、足迹 ZADD。
     */
    public void markLiked(Long userId, Long commentId) {
        ensureCountBaseline(commentId);
        executeToggle(userId, commentId, 1);
    }

    /**
     * 取消点赞实时更新：计数 -1（clamp 不小于 0）、已赞集合 SREM、足迹 ZREM。
     */
    public void markUnliked(Long userId, Long commentId) {
        ensureCountBaseline(commentId);
        executeToggle(userId, commentId, -1);
    }

    /**
     * 批量过滤「当前用户已点赞的评论 ID」（读侧 Redis 优先，冷缓存回源数据库重建）。
     */
    public List<Long> filterLikedCommentIds(Long userId, List<Long> commentIds) {
        if (CollUtil.isEmpty(commentIds)) {
            return List.of();
        }
        String setKey = RedisKeyConstants.buildUserCommentLikeSetKey(userId);
        if (isSetInitialized(setKey)) {
            return commentIds.stream()
                    .filter(commentId -> Boolean.TRUE.equals(
                            stringRedisTemplate.opsForSet().isMember(setKey, String.valueOf(commentId))))
                    .toList();
        }
        // 冷缓存：回源数据库全量重建 Set + ZSet
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
     * 判断用户是否已点赞该评论（点赞/取消入口的实时门卫，替代布隆过滤器）。
     *
     * <p>Set 已初始化 → SISMEMBER 精确判断；未初始化 → 先回源数据库全量重建 Set+ZSet 再判断，
     * 保证取消点赞时能精确命中（SREM 返回 1 才会减计数）。Redis 异常时兜底直查数据库。</p>
     *
     * @param userId    用户 ID
     * @param commentId 评论 ID
     * @return 是否已点赞
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
     * 我的点赞足迹分页（member=commentId，score=点赞时间，最新在前）。
     *
     * @param userId   用户 ID
     * @param pageNo   页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 分页的已赞评论 ID + 总数
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
