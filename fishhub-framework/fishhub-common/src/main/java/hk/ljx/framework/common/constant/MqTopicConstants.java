package hk.ljx.framework.common.constant;

/**
 * RocketMQ Topic 常量
 */
public interface MqTopicConstants {

    /**
     * Topic: 笔记变更事件
     */
    String TOPIC_NOTE_CHANGED = "NoteChangedTopic";

    /**
     * Topic: 用户变更事件（资料更新等）
     */
    String TOPIC_USER_CHANGED = "UserChangedTopic";

    /**
     * Topic: 笔记点赞数计数
     */
    String TOPIC_COUNT_NOTE_LIKE = "CountNoteLikeTopic";

    /**
     * Topic: 笔记收藏数计数
     */
    String TOPIC_COUNT_NOTE_COLLECT = "CountNoteCollectTopic";

    /**
     * Topic: 关注数计数
     */
    String TOPIC_COUNT_FOLLOWING = "CountFollowingTopic";

    /**
     * Topic: 评论变更事件
     */
    String TOPIC_COMMENT_CHANGED = "CommentChangedTopic";

    /**
     * Topic: 评论点赞/取消点赞
     */
    String TOPIC_COMMENT_LIKE_OR_UNLIKE = "CommentLikeUnlikeTopic";

    /**
     * Topic: 评论热度值更新
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
}
