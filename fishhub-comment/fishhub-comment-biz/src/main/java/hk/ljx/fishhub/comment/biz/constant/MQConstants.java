package hk.ljx.fishhub.comment.biz.constant;

import hk.ljx.framework.common.constant.MqTopicConstants;

/**
 * 评论模块 MQ 常量
 */
public interface MQConstants extends MqTopicConstants {

    /**
     * Topic: 评论发布（模块内部：请求同步投递 -> 批量落库消费者）
     */
    String TOPIC_PUBLISH_COMMENT = "PublishCommentTopic";

    /**
     * Topic: 删除评论（模块内部）
     */
    String TOPIC_DELETE_COMMENT = "DeleteCommentTopic";

    /**
     * Tag 标签：点赞（comment 模块内部 CommentLikeUnlikeTopic 使用）
     */
    String TAG_LIKE = "Like";

    /**
     * Tag 标签：取消点赞（comment 模块内部 CommentLikeUnlikeTopic 使用）
     */
    String TAG_UNLIKE = "UnLike";

}
