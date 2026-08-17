package hk.ljx.fishhub.user.relation.biz.constant;


public interface MQConstants {

    /**
     * Topic: 关注、取关共用一个
     */
    String TOPIC_FOLLOW_OR_UNFOLLOW = "FollowUnfollowTopic";

    /**
     * Topic: 关注数计数（统一事件：一次消费同时累加关注数与粉丝数）
     */
    String TOPIC_COUNT_FOLLOWING = "CountFollowingTopic";

    /**
     * 关注标签
     */
    String TAG_FOLLOW = "Follow";

    /**
     * 取关标签
     */
    String TAG_UNFOLLOW = "Unfollow";
}
