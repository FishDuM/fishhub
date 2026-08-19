package hk.ljx.fishhub.distributed.id.generator.constant;


public interface ApiConstants {

    /**
     * 服务名称
     */
    String SERVICE_NAME = "fishhub-distributed-id-generator";

    /**
     * Leaf Snowflake 业务标识：评论 ID
     */
    String BIZ_TAG_COMMENT_ID = "leaf-snowflake-comment-id";

    /**
     * Leaf Snowflake 业务标识：笔记 ID
     */
    String BIZ_TAG_NOTE_ID = "leaf-snowflake-note-id";

    /**
     * Leaf Snowflake 业务标识：用户 ID
     */
    String BIZ_TAG_USER_ID = "leaf-snowflake-user-id";

    /**
     * Leaf Snowflake 业务标识：小鱼号 ID
     */
    String BIZ_TAG_FISHHUB_ID = "leaf-snowflake-fishhub-id";
}
