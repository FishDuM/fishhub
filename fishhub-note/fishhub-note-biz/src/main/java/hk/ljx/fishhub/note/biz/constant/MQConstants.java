package hk.ljx.fishhub.note.biz.constant;

import hk.ljx.framework.common.constant.MqTopicConstants;

/**
 * 笔记模块 MQ 常量
 */
public interface MQConstants extends MqTopicConstants {

    /**
     * Topic: 点赞、取消点赞共用一个（模块内部）
     */
    String TOPIC_LIKE_OR_UNLIKE = "LikeUnlikeTopic";

    /**
     * Topic: 收藏、取消收藏共用一个（模块内部）
     */
    String TOPIC_COLLECT_OR_UN_COLLECT = "CollectUnCollectTopic";

    /**
     * Tag 标签：点赞
     */
    String TAG_LIKE = "Like";

    /**
     * Tag 标签：取消点赞
     */
    String TAG_UNLIKE = "Unlike";

    /**
     * Tag 标签：收藏
     */
    String TAG_COLLECT = "Collect";

    /**
     * Tag 标签：取消收藏
     */
    String TAG_UN_COLLECT = "UnCollect";
}
