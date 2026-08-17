package hk.ljx.fishhub.comment.biz.constant;


public class RedisKeyConstants {

    /**
     * 版本必须比总数缓存的最大 TTL（15 分钟）更长，避免版本先过期而旧版本数据仍可读取。
     */
    public static final long ONE_LEVEL_COMMENT_TOTAL_CACHE_VERSION_EXPIRE_SECONDS = 20 * 60L;

    /**
     * Key 前缀：一级评论的 first_reply_comment_id 字段值是否更新标识
     */
    private static final String HAVE_FIRST_REPLY_COMMENT_KEY_PREFIX = "comment:havaFirstReplyCommentId:";

    /**
     * Key 前缀：布隆过滤器 - 用户点赞的评论。
     * v2：由 Redisson RBloomFilter 管理
     */
    private static final String BLOOM_COMMENT_LIKES_KEY_PREFIX = "bloom:comment:likes:v2:";

    /**
     * 一级评论分页总数缓存。不能复用 count:note:*，后者由计数服务作为 Hash 使用。
     */
    private static final String ONE_LEVEL_COMMENT_TOTAL_CACHE_KEY_PREFIX = "cache:comment:one-level-total:";

    private static final String ONE_LEVEL_COMMENT_TOTAL_CACHE_VERSION_KEY_PREFIX = "version:comment:one-level-total:";

    private static final String ONE_LEVEL_COMMENT_TOTAL_CACHE_LOCK_KEY_PREFIX = "lock:comment:one-level-total:";

    /**
     * 评论维度计数 Key 前缀
     */
    private static final String COUNT_COMMENT_KEY_PREFIX = "count:comment:";

    /**
     * Key 前缀：评论分页 ZSET
     */
    private static final String COMMENT_LIST_KEY_PREFIX = "comment:list:";

    /** 评论分页 ZSET 重建单飞锁 Key 前缀 */
    private static final String COMMENT_LIST_REBUILD_LOCK_KEY_PREFIX = "lock:comment:list:rebuild:";
/** 子评论分页 ZSET 重建单飞锁 Key 前缀 */
    private static final String CHILD_COMMENT_LIST_REBUILD_LOCK_KEY_PREFIX = "lock:comment:childList:rebuild:";

    /**
     * Key 前缀：二级评论分页 ZSET
     */
    private static final String CHILD_COMMENT_LIST_KEY_PREFIX = "comment:childList:";

    /**
     * Key 前缀：评论详情 JSON
     */
    private static final String COMMENT_DETAIL_KEY_PREFIX = "comment:detail:v2:";

    /**
     * Hash Field 键：评论总数
     */
    public static final String FIELD_COMMENT_TOTAL = "commentTotal";

    /**
     * Hash Field: 子评论总数
     */
    public static final String FIELD_CHILD_COMMENT_TOTAL = "childCommentTotal";

    /**
     * Hash Field: 点赞总数
     */
    public static final String FIELD_LIKE_TOTAL = "likeTotal";

    /**
     * 构建完整 KEY
     * @param commentId
     * @return
     */
    public static String buildHaveFirstReplyCommentKey(Long commentId) {
        return HAVE_FIRST_REPLY_COMMENT_KEY_PREFIX + commentId;
    }

    /**
     * 构建 布隆过滤器 - 用户点赞的评论 完整 KEY
     * @param userId
     * @return
     */
    public static String buildBloomCommentLikesKey(Long userId) {
        return BLOOM_COMMENT_LIKES_KEY_PREFIX + userId;
    }

    public static String buildOneLevelCommentTotalCacheKey(Long noteId, String version) {
        return ONE_LEVEL_COMMENT_TOTAL_CACHE_KEY_PREFIX + noteId + ":v:" + version;
    }

    public static String buildOneLevelCommentTotalCacheVersionKey(Long noteId) {
        return ONE_LEVEL_COMMENT_TOTAL_CACHE_VERSION_KEY_PREFIX + noteId;
    }

    public static String buildOneLevelCommentTotalCacheLockKey(Long noteId) {
        return ONE_LEVEL_COMMENT_TOTAL_CACHE_LOCK_KEY_PREFIX + noteId;
    }

    /**
     * 构建评论分页 ZSET 完整 KEY
     * @param noteId
     * @return
     */
    public static String buildCommentListKey(Long noteId) {
        return COMMENT_LIST_KEY_PREFIX + noteId;
    }

    /** 构建评论分页 ZSET 重建单飞锁完整 KEY */
    public static String buildCommentListRebuildLockKey(Long noteId) {
        return COMMENT_LIST_REBUILD_LOCK_KEY_PREFIX + noteId;
    }

    /**
     * 构建子评论分页 ZSET 完整 KEY
     * @param commentId
     * @return
     */
    public static String buildChildCommentListKey(Long commentId) {
        return CHILD_COMMENT_LIST_KEY_PREFIX + commentId;
    }

    /** 构建子评论分页 ZSET 重建单飞锁完整 KEY */
    public static String buildChildCommentListRebuildLockKey(Long parentCommentId) {
        return CHILD_COMMENT_LIST_REBUILD_LOCK_KEY_PREFIX + parentCommentId;
    }

    /**
     * 构建评论详情完整 KEY
     * @param commentId
     * @return
     */
    public static String buildCommentDetailKey(Object commentId) {
        return COMMENT_DETAIL_KEY_PREFIX + commentId;
    }

    /**
     * 构建评论维度计数 Key
     * @param commentId
     * @return
     */
    public static String buildCountCommentKey(Long commentId) {
        return COUNT_COMMENT_KEY_PREFIX + commentId;
    }

}
