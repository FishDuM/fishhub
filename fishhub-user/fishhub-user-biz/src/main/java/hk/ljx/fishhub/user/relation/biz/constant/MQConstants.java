package hk.ljx.fishhub.user.relation.biz.constant;

import hk.ljx.framework.common.constant.MqTopicConstants;

/**
 * 用户关系模块 MQ 常量
 */
public interface MQConstants extends MqTopicConstants {

    /**
     * Topic: 关注、取关共用一个（模块内部）
     */
    String TOPIC_FOLLOW_OR_UNFOLLOW = "FollowUnfollowTopic";

    /**
     * 关注标签
     */
    String TAG_FOLLOW = "Follow";

    /**
     * 取关标签
     */
    String TAG_UNFOLLOW = "Unfollow";
}
