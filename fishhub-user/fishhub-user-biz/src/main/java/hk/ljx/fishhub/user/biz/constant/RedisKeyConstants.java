package hk.ljx.fishhub.user.biz.constant;


public class RedisKeyConstants {

    /**
     * 用户信息数据 KEY 前缀
     */
    private static final String USER_INFO_KEY_PREFIX = "user:info:";

    /**
     * 用户主页信息数据 KEY 前缀
     */
    private static final String USER_PROFILE_KEY_PREFIX = "user:profile:v3:";

    /** 用户角色权限快照 Key 前缀 */
    private static final String USER_ROLE_PERMISSION_KEY_PREFIX = "user:role-permission:";

    /**
     * 构建角色信息对应的 KEY
     * @param userId
     * @return
     */
    public static String buildUserInfoKey(Long userId) {
        return USER_INFO_KEY_PREFIX + userId;
    }

    /**
     * 构建角色主页信息对应的 KEY
     * @param userId
     * @return
     */
    public static String buildUserProfileKey(Long userId) {
        return USER_PROFILE_KEY_PREFIX + userId;
    }

    /** 构建用户角色权限快照 KEY */
    public static String buildUserRolePermissionKey(Long userId) {
        return USER_ROLE_PERMISSION_KEY_PREFIX + userId;
    }
}
