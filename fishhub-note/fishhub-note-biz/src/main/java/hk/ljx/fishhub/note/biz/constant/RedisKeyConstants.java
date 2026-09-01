package hk.ljx.fishhub.note.biz.constant;

public class RedisKeyConstants {

    /**
     * 笔记详情 KEY 前缀
     */
    public static final String NOTE_DETAIL_KEY = "note:detail:";

    /**
     * 笔记访问控制最小快照
     */
    private static final String NOTE_ACCESS_KEY = "note:access:";

    // 笔记访问快照重建单飞锁前缀（防热点笔记击穿）
    private static final String NOTE_ACCESS_REBUILD_LOCK_KEY = "lock:note:access:";

    // 用户互动缓存（点赞/收藏 Set）初始化单飞锁前缀
    private static final String USER_NOTE_INTERACTION_INIT_LOCK_KEY = "lock:note:interaction:init:";

    // 发现页 Feed 流 Key 前缀
    private static final String DISCOVER_FEED_CURSOR_KEY = "feed:discover:channel:";

    private static final String DISCOVER_FEED_CURSOR_LOCK_KEY = "lock:feed:discover:channel:";

    private static final String ACTIVE_TOPIC_SNAPSHOT_KEY = "topic:active:snapshot";

    private static final String ACTIVE_CHANNEL_SNAPSHOT_KEY = "channel:active:snapshot";

    /**
     * 已发布笔记列表 KEY 前缀
     */
    private static final String PUBLISHED_NOTE_LIST_KEY = "note:published:list:";

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
     */
    public static String buildPublishedNoteListKey(Long userId) {
        return PUBLISHED_NOTE_LIST_KEY + userId;
    }

    /**
     * 构建完整的笔记详情 KEY
     */
    public static String buildNoteDetailKey(Long noteId) {
        return NOTE_DETAIL_KEY + noteId;
    }

    public static String buildNoteAccessKey(Long noteId) {
        return NOTE_ACCESS_KEY + noteId;
    }

    public static String buildNoteAccessRebuildLockKey(Long noteId) {
        return NOTE_ACCESS_REBUILD_LOCK_KEY + noteId;
    }

    public static String buildUserNoteInteractionInitLockKey(Long userId) {
        return USER_NOTE_INTERACTION_INIT_LOCK_KEY + userId;
    }

    /**
     * 构建发现页 Feed 游标分页缓存 KEY（直接以 channelId 和 cursor 拼接，30s TTL 自动过期）
     */
    public static String buildDiscoverFeedCursorKey(Long channelId, Long cursor) {
        return DISCOVER_FEED_CURSOR_KEY + (channelId == null ? 0 : channelId)
                + ":cursor:" + (cursor == null ? "first" : cursor);
    }

    public static String buildDiscoverFeedCursorLockKey(Long channelId, Long cursor) {
        return DISCOVER_FEED_CURSOR_LOCK_KEY + (channelId == null ? 0 : channelId)
                + ":cursor:" + (cursor == null ? "first" : cursor);
    }

    public static String activeTopicSnapshotKey() {
        return ACTIVE_TOPIC_SNAPSHOT_KEY;
    }

    public static String activeChannelSnapshotKey() {
        return ACTIVE_CHANNEL_SNAPSHOT_KEY;
    }

    public static String buildUserNoteLikeSetKey(Long userId) {
        return buildUserNoteLikeZSetKey(userId);
    }

    public static String buildUserNoteCollectSetKey(Long userId) {
        return buildUserNoteCollectZSetKey(userId);
    }

    public static String buildUserNoteLikeZSetKey(Long userId) {
        return USER_NOTE_LIKE_ZSET_KEY + userId;
    }

    public static String buildUserNoteCollectZSetKey(Long userId) {
        return USER_NOTE_COLLECT_ZSET_KEY + userId;
    }
}
