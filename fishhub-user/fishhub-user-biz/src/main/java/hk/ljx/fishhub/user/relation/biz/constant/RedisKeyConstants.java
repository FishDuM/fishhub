package hk.ljx.fishhub.user.relation.biz.constant;


public class RedisKeyConstants {

    /** 关注列表 KEY 前缀 */
    private static final String USER_FOLLOWING_KEY_PREFIX = "following:";

    /** 粉丝列表（反向）KEY 前缀 */
    private static final String USER_FANS_KEY_PREFIX = "fans:";

    /** 关注/粉丝列表重建单飞锁前缀 */
    private static final String RELATION_LIST_REBUILD_LOCK_PREFIX = "lock:relation:list:rebuild:";

    public static String buildUserFollowingKey(Long userId) {
        return USER_FOLLOWING_KEY_PREFIX + userId;
    }

    public static String buildUserFansKey(Long userId) {
        return USER_FANS_KEY_PREFIX + userId;
    }

    public static String buildFollowingRebuildLockKey(Long userId) {
        return RELATION_LIST_REBUILD_LOCK_PREFIX + "following:" + userId;
    }

    public static String buildFansRebuildLockKey(Long userId) {
        return RELATION_LIST_REBUILD_LOCK_PREFIX + "fans:" + userId;
    }

}
