package hk.ljx.fishhub.user.biz.util;

import cn.hutool.extra.servlet.JakartaServletUtil;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端真实 IP 解析。
 */
public final class ClientIpUtils {

    private ClientIpUtils() {
    }

    public static String resolveClientIp(HttpServletRequest request) {
        return JakartaServletUtil.getClientIP(request, "X-Real-IP", "X-Forwarded-For");
    }
}
