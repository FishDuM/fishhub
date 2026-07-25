package hk.ljx.fishhub.data.align.constant;

public class RedisKeyConstants {

    /**
     * 布隆过滤器：日增量变更数据，用户笔记点赞，取消点赞 前缀
     */
    public static final String BLOOM_TODAY_NOTE_LIKE_LIST_KEY = "bloom:dataAlign:note:likes:";


    /**
     * 构建完整的布隆过滤器：日增量变更数据，用户笔记点赞，取消点赞 KEY
     * @param date
     * @return
     */
    public static String buildBloomUserNoteLikeListKey(String date) {
        return BLOOM_TODAY_NOTE_LIKE_LIST_KEY + date;
    }

    /**
     * 布隆过滤器：日增量变更数据，用户笔记收藏，取消收藏 前缀
     */
    public static final String BLOOM_TODAY_NOTE_COLLECT_LIST_KEY = "bloom:dataAlign:note:collects:";

    /**
     * 构建完整的布隆过滤器：日增量变更数据，用户笔记收藏，取消收藏 KEY
     * @param date
     * @return
     */
    public static String buildBloomUserNoteCollectListKey(String date) {
        return BLOOM_TODAY_NOTE_COLLECT_LIST_KEY + date;
    }

    /**
     * 布隆过滤器：日增量变更数据，用户笔记发布，删除 前缀
     */
    public static final String BLOOM_TODAY_USER_NOTE_OPERATOR_LIST_KEY = "bloom:dataAlign:user:note:operators:";

    /**
     * 构建完整的布隆过滤器：日增量变更数据，用户笔记发布，删除 KEY
     * @param date
     * @return
     */
    public static String buildBloomUserNoteOperateListKey(String date) {
        return BLOOM_TODAY_USER_NOTE_OPERATOR_LIST_KEY + date;
    }

    /**
     * 布隆过滤器：日增量变更数据，用户关注数 前缀
     */
    public static final String BLOOM_TODAY_USER_FOLLOW_LIST_KEY = "bloom:dataAlign:user:follows:";

    /**
     * 布隆过滤器：日增量变更数据，用户粉丝数 前缀
     */
    public static final String BLOOM_TODAY_USER_FANS_LIST_KEY = "bloom:dataAlign:user:fans:";

    /**
     * 构建完整的布隆过滤器：日增量变更数据，用户关注数 KEY
     * @param date
     * @return
     */
    public static String buildBloomUserFollowListKey(String date) {
        return BLOOM_TODAY_USER_FOLLOW_LIST_KEY + date;
    }

    /**
     * 构建完整的布隆过滤器：日增量变更数据，用户粉丝数 KEY
     * @param date
     * @return
     */
    public static String buildBloomUserFansListKey(String date) {
        return BLOOM_TODAY_USER_FANS_LIST_KEY + date;
    }

    /**
     * 用户维度计数 Key 前缀
     */
    private static final String COUNT_USER_KEY_PREFIX = "count:user:";

    /**
     * Hash Field: 关注总数
     */
    public static final String FIELD_FOLLOWING_TOTAL = "followingTotal";

    /**
     * 构建用户维度计数 Key
     * @param userId
     * @return
     */
    public static String buildCountUserKey(Long userId) {
        return COUNT_USER_KEY_PREFIX + userId;
    }
}
