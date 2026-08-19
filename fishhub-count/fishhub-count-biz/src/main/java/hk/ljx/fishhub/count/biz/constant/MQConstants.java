package hk.ljx.fishhub.count.biz.constant;


public interface MQConstants {

    /**
     * Topic: 关注数计数
     */
    String TOPIC_COUNT_FOLLOWING = "CountFollowingTopic";

    /**
     * Topic: 计数 - 笔记点赞数
     */
    String TOPIC_COUNT_NOTE_LIKE = "CountNoteLikeTopic";

    /**
     * Topic: 计数 - 笔记收藏数
     */
    String TOPIC_COUNT_NOTE_COLLECT = "CountNoteCollectTopic";

    /**
     * Topic: 笔记评论总数计数
     */
    String TOPIC_COMMENT_CHANGED = "CommentChangedTopic";

    /**
     * Topic: 评论热度值更新
     */
    String TOPIC_COMMENT_HEAT_UPDATE = "CommentHeatUpdateTopic";

    Integer COMMENT_CHANGE_TYPE_PUBLISH = 1;

    Integer COMMENT_CHANGE_TYPE_DELETE = 0;

    /**
     * Topic: 笔记操作（发布、删除）
     */
    String TOPIC_NOTE_CHANGED = "NoteChangedTopic";

    /**
     * Topic: 评论点赞数更新
     */
    String TOPIC_COMMENT_LIKE_OR_UNLIKE = "CommentLikeUnlikeTopic";

    /**
     * Topic: 删除本地缓存 —— 评论详情
     */
    String TOPIC_DELETE_COMMENT_LOCAL_CACHE = "DeleteCommentDetailLocalCacheTopic";

}
