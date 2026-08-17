package hk.ljx.fishhub.auth.constant;


public class RedisKeyConstants {

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
