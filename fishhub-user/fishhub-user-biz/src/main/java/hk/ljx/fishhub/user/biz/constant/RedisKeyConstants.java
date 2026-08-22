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

    /** 可操作用户缓存 Key 前缀 */
    private static final String USER_ACTIVE_KEY_PREFIX = "user:active:";

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

    public static String buildUserActiveKey(Long userId) {
        return USER_ACTIVE_KEY_PREFIX + userId;
    }

    /**
     * 验证码 KEY 前缀
     */
    private static final String VERIFICATION_CODE_KEY_PREFIX = "verification_code:";

    /**
     * 每手机号分钟限流 KEY 前缀
     */
    private static final String PHONE_RATE_LIMIT_KEY_PREFIX = "verification_code:minute:phone:";

    /**
     * 每 IP 分钟限流 KEY 前缀（依赖网关透传 X-Forwarded-For）
     */
    private static final String IP_RATE_LIMIT_KEY_PREFIX = "verification_code:minute:ip:";

    /**
     * 构建验证码 KEY
     * @param phone
     * @return
     */
    public static String buildVerificationCodeKey(String phone) {
        return VERIFICATION_CODE_KEY_PREFIX + phone;
    }

    public static String buildPhoneRateLimitKey(String phone) {
        return PHONE_RATE_LIMIT_KEY_PREFIX + phone;
    }

    public static String buildIpRateLimitKey(String ip) {
        return IP_RATE_LIMIT_KEY_PREFIX + ip;
    }
}
