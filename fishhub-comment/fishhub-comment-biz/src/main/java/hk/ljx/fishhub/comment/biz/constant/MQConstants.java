package hk.ljx.fishhub.comment.biz.constant;


public interface MQConstants {

    /**
     * Topic: 评论发布
     */
    String TOPIC_PUBLISH_COMMENT = "PublishCommentTopic";

    /**
     * Topic: 评论变更统一事件（发布、删除），由 count 模块与评论模块自身的多个 consumer group 订阅
     */
    String TOPIC_COMMENT_CHANGED = "CommentChangedTopic";

    /**
     * Topic: 评论点赞、取消点赞共用一个 Topic
     */
    String TOPIC_COMMENT_LIKE_OR_UNLIKE = "CommentLikeUnlikeTopic";

    /**
     * 评论点赞关系实际变化后产生的计数事件
     */
    String TOPIC_APPLIED_COMMENT_LIKE_OR_UNLIKE = "AppliedCommentLikeUnlikeTopic";

    /**
     * Topic: 删除本地缓存 —— 评论详情（模块内广播）
     */
    String TOPIC_DELETE_COMMENT_LOCAL_CACHE = "DeleteCommentDetailLocalCacheTopic";

    /**
     * Topic: 删除评论
     */
    String TOPIC_DELETE_COMMENT = "DeleteCommentTopic";

    /**
     * Topic: 评论热度值更新（评论点赞聚合落库后触发重算）
     */
    String TOPIC_COMMENT_HEAT_UPDATE = "CommentHeatUpdateTopic";

    /**
     * 评论变更类型：发布
     */
    Integer COMMENT_CHANGE_TYPE_PUBLISH = 1;

    /**
     * 评论变更类型：删除
     */
    Integer COMMENT_CHANGE_TYPE_DELETE = 0;

    /**
     * Tag 标签：点赞
     */
    String TAG_LIKE = "Like";

    /**
     * Tag 标签：取消点赞
     */
    String TAG_UNLIKE = "UnLike";

}
