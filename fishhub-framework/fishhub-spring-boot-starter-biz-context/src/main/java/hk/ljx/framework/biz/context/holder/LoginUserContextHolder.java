package hk.ljx.framework.biz.context.holder;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 登录用户上下文持有者（基于 Alibaba TTL，支持线程池复用场景下的跨线程上下文传递）
 */
public final class LoginUserContextHolder {

    private static final ThreadLocal<Long> LOGIN_USER_CONTEXT_THREAD_LOCAL = new TransmittableThreadLocal<>();

    private LoginUserContextHolder() {}

    /**
     * 设置用户 ID
     */
    public static void setUserId(Long value) {
        LOGIN_USER_CONTEXT_THREAD_LOCAL.set(value);
    }

    /**
     * 设置用户 ID（兼容 String / Object 类型入参）
     */
    public static void setUserId(Object value) {
        if (value == null) {
            LOGIN_USER_CONTEXT_THREAD_LOCAL.remove();
            return;
        }
        if (value instanceof Number number) {
            LOGIN_USER_CONTEXT_THREAD_LOCAL.set(number.longValue());
            return;
        }
        try {
            LOGIN_USER_CONTEXT_THREAD_LOCAL.set(Long.valueOf(value.toString().trim()));
        } catch (NumberFormatException e) {
            LOGIN_USER_CONTEXT_THREAD_LOCAL.remove();
        }
    }

    /**
     * 获取用户 ID
     */
    public static Long getUserId() {
        return LOGIN_USER_CONTEXT_THREAD_LOCAL.get();
    }

    /**
     * 删除 ThreadLocal
     */
    public static void remove() {
        LOGIN_USER_CONTEXT_THREAD_LOCAL.remove();
    }
}
