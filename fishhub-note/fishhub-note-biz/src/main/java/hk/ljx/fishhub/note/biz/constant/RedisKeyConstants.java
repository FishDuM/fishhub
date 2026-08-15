package hk.ljx.fishhub.note.biz.constant;


public class RedisKeyConstants {

    /**
     * 笔记详情 KEY 前缀
     */
    public static final String NOTE_DETAIL_KEY = "note:detail:";

    /**
     * 笔记访问控制最小快照。详情、评论等高频读路径只需要这几个字段来做权限判断。
     */
    private static final String NOTE_ACCESS_KEY = "note:access:";

    private static final String DISCOVER_FEED_VERSION_KEY = "feed:discover:version";

    private static final String DISCOVER_FEED_CURSOR_KEY = "feed:discover:cursor:";

    private static final String DISCOVER_FEED_CURSOR_LOCK_KEY = "lock:feed:discover:cursor:";

    private static final String ACTIVE_TOPIC_SNAPSHOT_KEY = "topic:active:snapshot";

    /**
     * 已发布笔记列表 KEY 前缀
     */
    private static final String PUBLISHED_NOTE_LIST_KEY = "note:published:list:";

    /**
     * 用户笔记点赞状态 Set 前缀
     */
    private static final String USER_NOTE_LIKE_SET_KEY = "set:note:likes:";

    /**
     * 用户笔记收藏状态 Set 前缀
     */
    private static final String USER_NOTE_COLLECT_SET_KEY = "set:note:collects:";

    /**
     * 用户笔记点赞列表 ZSet 前缀
     */
    public static final String USER_NOTE_LIKE_ZSET_KEY = "user:note:likes:";

    /**
     * 用户笔记收藏列表 ZSet 前缀
     */
    public static final String USER_NOTE_COLLECT_ZSET_KEY = "user:note:collects:";

    /**
     * 构建完整的已发布笔记列表 KEY
     * @param userId
     * @return
     */
    public static String buildPublishedNoteListKey(Long userId) {
        return PUBLISHED_NOTE_LIST_KEY + userId;
    }

    /**
     * 构建完整的笔记详情 KEY
     * @param noteId
     * @return
     */
    public static String buildNoteDetailKey(Long noteId) {
        return NOTE_DETAIL_KEY + noteId;
    }

    public static String buildNoteAccessKey(Long noteId) {
        return NOTE_ACCESS_KEY + noteId;
    }

    public static String discoverFeedVersionKey() {
        return DISCOVER_FEED_VERSION_KEY;
    }

    public static String buildDiscoverFeedCursorKey(String version, Long channelId, Long cursor) {
        return DISCOVER_FEED_CURSOR_KEY + version + ":channel:" + (channelId == null ? 0 : channelId)
                + ":cursor:" + (cursor == null ? "first" : cursor);
    }

    public static String buildDiscoverFeedCursorLockKey(String version, Long channelId, Long cursor) {
        return DISCOVER_FEED_CURSOR_LOCK_KEY + version + ":channel:" + (channelId == null ? 0 : channelId)
                + ":cursor:" + (cursor == null ? "first" : cursor);
    }

    public static String activeTopicSnapshotKey() {
        return ACTIVE_TOPIC_SNAPSHOT_KEY;
    }


    public static String buildUserNoteLikeSetKey(Long userId) {
        return USER_NOTE_LIKE_SET_KEY + userId;
    }

    public static String buildUserNoteCollectSetKey(Long userId) {
        return USER_NOTE_COLLECT_SET_KEY + userId;
    }

    /**
     * 构建完整的用户笔记点赞列表 ZSet KEY
     * @param userId
     * @return
     */
    public static String buildUserNoteLikeZSetKey(Long userId) {
        return USER_NOTE_LIKE_ZSET_KEY + userId;
    }

    /**
     * 构建完整的用户笔记收藏列表 ZSet KEY
     * @param userId
     * @return
     */
    public static String buildUserNoteCollectZSetKey(Long userId) {
        return USER_NOTE_COLLECT_ZSET_KEY + userId;
    }

}
