package hk.ljx.fishhub.auth.constant;


public class RedisKeyConstants {

    /**
     * 验证码 KEY 前缀
     */
    private static final String VERIFICATION_CODE_KEY_PREFIX = "verification_code:";

    /**
     * 全局发送限流 KEY（固定 Key，按分钟窗口计数）
     */
    public static final String GLOBAL_RATE_LIMIT_KEY = "verification_code:global:minute";


    /**
     * 构建验证码 KEY
     * @param phone
     * @return
     */
    public static String buildVerificationCodeKey(String phone) {
        return VERIFICATION_CODE_KEY_PREFIX + phone;
    }

}
