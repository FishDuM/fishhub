package hk.ljx.fishhub.gateway.filter;

import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import static hk.ljx.framework.common.constant.GlobalConstants.USER_ID;
import static hk.ljx.fishhub.gateway.auth.SaTokenConfigure.USER_ID_ATTR;

@Component
@Slf4j
@Order(-90)
public class AddUserId2HeaderFilter implements GlobalFilter {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String X_REAL_IP = "X-Real-IP";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpRequest.Builder requestBuilder = request.mutate()
                .headers(headers -> headers.remove(USER_ID)); // 剥离外部伪造的 userId

        // 真实客户端 IP 已由 Nginx realip 模块递归清洗并注入 X-Real-IP；若直连则兜底取 TCP 连接 IP
        HttpHeaders headers = request.getHeaders();
        String clientIp = headers != null ? headers.getFirst(X_REAL_IP) : null;
        if (StringUtils.isBlank(clientIp) && request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
            clientIp = request.getRemoteAddress().getAddress().getHostAddress();
        }
        if (StringUtils.isNotBlank(clientIp)) {
            requestBuilder.header(X_REAL_IP, clientIp);
        }

        // 优先复用前置鉴权已提取的 loginId
        Object cachedLoginId = exchange.getAttribute(USER_ID_ATTR);
        if (cachedLoginId != null) {
            requestBuilder.header(USER_ID, cachedLoginId.toString());
            return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
        }

        // 免登录路径：异步解析 Token 避免阻塞 Netty EventLoop
        return Mono.fromCallable(() -> resolveLoginIdFromRequest(request))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(loginId -> {
                    requestBuilder.header(USER_ID, loginId.toString());
                    return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
                })
                .switchIfEmpty(Mono.defer(() -> chain.filter(exchange.mutate().request(requestBuilder.build()).build())));
    }

    private Object resolveLoginIdFromRequest(ServerHttpRequest request) {
        // 1. Header 提取
        HttpHeaders headers = request.getHeaders();
        if (headers != null) {
            String authorization = headers.getFirst(AUTHORIZATION);
            if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
                String token = authorization.substring(BEARER_PREFIX.length()).trim();
                Object loginId = StpUtil.getLoginIdByToken(token);
                if (loginId != null) {
                    return loginId;
                }
            }
        }

        // 2. Cookie 提取（HttpOnly 会话）
        HttpCookie authCookie = request.getCookies().getFirst(AUTHORIZATION);
        if (authCookie != null && StringUtils.isNotBlank(authCookie.getValue())) {
            return StpUtil.getLoginIdByToken(authCookie.getValue().trim());
        }
        return null;
    }
}
