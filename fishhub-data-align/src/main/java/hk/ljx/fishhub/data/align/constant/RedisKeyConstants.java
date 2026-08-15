package hk.ljx.fishhub.data.align.constant;

public final class RedisKeyConstants {

    private static final String DAILY_CHANGE_DEDUP_PREFIX = "dedup:dataAlign:";
    private static final String COUNT_USER_VERSION_KEY_PREFIX = "version:count:user:";
    private static final String COUNT_NOTE_KEY_PREFIX = "count:note:";

    public static final String FIELD_LIKE_TOTAL = "likeTotal";
    public static final String FIELD_COLLECT_TOTAL = "collectTotal";

    private RedisKeyConstants() {
    }

    public static String buildCountNoteKey(Long noteId) {
        return COUNT_NOTE_KEY_PREFIX + noteId;
    }

    public static String buildCountUserCacheVersionKey(Long userId) {
        return COUNT_USER_VERSION_KEY_PREFIX + userId;
    }

    public static String buildDailyNoteLikeNoteIdsDedupKey(String date) {
        return DAILY_CHANGE_DEDUP_PREFIX + "note:like:noteIds:" + date;
    }

    public static String buildDailyNoteLikeUserIdsDedupKey(String date) {
        return DAILY_CHANGE_DEDUP_PREFIX + "note:like:userIds:" + date;
    }

    public static String buildDailyNoteCollectNoteIdsDedupKey(String date) {
        return DAILY_CHANGE_DEDUP_PREFIX + "note:collect:noteIds:" + date;
    }

    public static String buildDailyNoteCollectUserIdsDedupKey(String date) {
        return DAILY_CHANGE_DEDUP_PREFIX + "note:collect:userIds:" + date;
    }

    public static String buildDailyNoteOperateUserIdsDedupKey(String date) {
        return DAILY_CHANGE_DEDUP_PREFIX + "user:note:operate:" + date;
    }

    public static String buildDailyFollowingUserIdsDedupKey(String date) {
        return DAILY_CHANGE_DEDUP_PREFIX + "user:following:" + date;
    }

    public static String buildDailyFansUserIdsDedupKey(String date) {
        return DAILY_CHANGE_DEDUP_PREFIX + "user:fans:" + date;
    }
}
