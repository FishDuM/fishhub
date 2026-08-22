package hk.ljx.fishhub.user.biz.constant;

import hk.ljx.framework.common.constant.MqTopicConstants;


public interface MQConstants extends MqTopicConstants {


    /**
     * Topic 主题：延迟双删 Redis 用户缓存
     */
    String TOPIC_DELAY_DELETE_USER_REDIS_CACHE = "DelayDeleteUserRedisCacheTopic";

}
