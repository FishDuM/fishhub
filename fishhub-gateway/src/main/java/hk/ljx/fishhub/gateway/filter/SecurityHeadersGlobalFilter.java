package hk.ljx.fishhub.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 安全响应头：兜底加固（前端静态资源的安全头由 Nginx 配置补充 CSP）。
 */
@Component
@Order(-95)
public class SecurityHeadersGlobalFilter implements GlobalFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse response = exchange.getResponse();
        HttpHeaders headers = response.getHeaders();
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "SAMEORIGIN");
        headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
        headers.set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        // 认证类接口响应不落浏览器缓存，避免 token/敏感信息进入缓存
        if (exchange.getRequest().getPath().value().startsWith("/auth/")) {
            headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
        }
        return chain.filter(exchange);
    }
}
