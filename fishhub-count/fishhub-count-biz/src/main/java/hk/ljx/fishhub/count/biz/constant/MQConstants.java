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
     * Topic: 计数 - 笔记点赞数落库
     */
    String TOPIC_COUNT_NOTE_LIKE_2_DB = "CountNoteLike2DBTTopic";

    /**
     * Topic: 计数 - 笔记收藏数落库
     */
    String TOPIC_COUNT_NOTE_COLLECT_2_DB = "CountNoteCollect2DBTTopic";

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
     * 评论点赞关系实际变化后产生的计数事件
     */
    String TOPIC_APPLIED_COMMENT_LIKE_OR_UNLIKE = "AppliedCommentLikeUnlikeTopic";

    /**
     * Topic: 计数 - 评论点赞数落库
     */
    String TOPIC_COUNT_COMMENT_LIKE_2_DB = "CountCommentLike2DBTTopic";

    /**
     * Topic: 删除本地缓存 —— 评论详情
     */
    String TOPIC_DELETE_COMMENT_LOCAL_CACHE = "DeleteCommentDetailLocalCacheTopic";

}
