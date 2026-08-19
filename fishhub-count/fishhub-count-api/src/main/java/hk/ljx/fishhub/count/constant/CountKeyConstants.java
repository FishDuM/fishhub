package hk.ljx.fishhub.count.constant;

/**
 * 计数服务 Redis Key 常量
 */
public final class CountKeyConstants {

    private CountKeyConstants() {
    }

    /**
     * 用户维度计数 Key 前缀
     */
    private static final String COUNT_USER_KEY_PREFIX = "count:user:";

    private static final String COUNT_USER_VERSION_KEY_PREFIX = "version:count:user:";

    /**
     * 笔记维度计数 Key 前缀
     */
    private static final String COUNT_NOTE_KEY_PREFIX = "count:note:";

    /**
     * 评论维度计数 Key 前缀
     */
    private static final String COUNT_COMMENT_KEY_PREFIX = "count:comment:";

    /**
     * Hash Field: 点赞总数
     */
    public static final String FIELD_LIKE_TOTAL = "likeTotal";

    /**
     * Hash Field: 笔记发布总数
     */
    public static final String FIELD_NOTE_TOTAL = "noteTotal";

    /**
     * Hash Field: 笔记收藏总数
     */
    public static final String FIELD_COLLECT_TOTAL = "collectTotal";

    /**
     * Hash Field: 笔记评论总数
     */
    public static final String FIELD_COMMENT_TOTAL = "commentTotal";

    /**
     * Hash Field: 粉丝总数
     */
    public static final String FIELD_FANS_TOTAL = "fansTotal";

    /**
     * Hash Field: 关注总数
     */
    public static final String FIELD_FOLLOWING_TOTAL = "followingTotal";

    /**
     * Hash Field: 子评论总数
     */
    public static final String FIELD_CHILD_COMMENT_TOTAL = "childCommentTotal";

    public static String buildCountUserCacheVersionKey(Long userId) {
        return COUNT_USER_VERSION_KEY_PREFIX + userId;
    }

    public static String buildCountUserSnapshotKey(Long userId, long version) {
        return COUNT_USER_KEY_PREFIX + userId + ":v:" + version;
    }

    /**
     * 构建笔记维度计数 Key
     */
    public static String buildCountNoteKey(Long noteId) {
        return COUNT_NOTE_KEY_PREFIX + noteId;
    }

    /**
     * 构建评论维度计数 Key
     */
    public static String buildCountCommentKey(Long commentId) {
        return COUNT_COMMENT_KEY_PREFIX + commentId;
    }
}
