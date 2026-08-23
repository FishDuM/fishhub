package hk.ljx.fishhub.gateway.filter;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
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

import java.util.HashSet;
import java.util.Set;

import static hk.ljx.framework.common.constant.GlobalConstants.USER_ID;
import static hk.ljx.fishhub.gateway.auth.SaTokenConfigure.USER_ID_ATTR;

@Component
@Slf4j
@Order(-90)
public class AddUserId2HeaderFilter implements GlobalFilter {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private static final String X_REAL_IP = "X-Real-IP";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_FORWARDED_PROTO = "X-Forwarded-Proto";
    private static final String X_FORWARDED_HOST = "X-Forwarded-Host";
    private static final String X_FORWARDED_PORT = "X-Forwarded-Port";
    private static final String FORWARDED = "Forwarded";

    /** 可信反向代理 IP 列表，仅当直连对端命中该列表时才信任转发头 */
    @Value("${fishhub.gateway.trusted-proxy-ips:}")
    private String trustedProxyIps;

    private volatile Set<String> trustedProxySet = Set.of();

    @PostConstruct
    public void initTrustedProxies() {
        this.trustedProxySet = StringUtils.isNotBlank(trustedProxyIps)
                ? Set.copyOf(StrUtil.splitTrim(trustedProxyIps, ','))
                : Set.of();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpRequest.Builder requestBuilder = request.mutate()
                .headers(headers -> {
                    headers.remove(USER_ID);
                    headers.remove(X_REAL_IP);
                    headers.remove(X_FORWARDED_FOR);
                    headers.remove(X_FORWARDED_PROTO);
                    headers.remove(X_FORWARDED_HOST);
                    headers.remove(X_FORWARDED_PORT);
                    headers.remove(FORWARDED);
                });

        String clientIp = resolveClientIp(request);
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
                    if (loginId != null) {
                        requestBuilder.header(USER_ID, loginId.toString());
                    }
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

    private String resolveClientIp(ServerHttpRequest request) {
        String peerIp = null;
        if (request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
            peerIp = request.getRemoteAddress().getAddress().getHostAddress();
        }
        HttpHeaders headers = request.getHeaders();
        if (headers != null && peerIp != null && trustedProxySet.contains(peerIp)) {
            String realIp = headers.getFirst(X_REAL_IP);
            if (isValidIp(realIp)) {
                return realIp;
            }
            String forwarded = headers.getFirst(X_FORWARDED_FOR);
            if (StringUtils.isNotBlank(forwarded)) {
                String first = forwarded.split(",")[0].trim();
                if (isValidIp(first)) {
                    return first;
                }
            }
        }
        return peerIp;
    }

    private static boolean isValidIp(String ip) {
        return StringUtils.isNotBlank(ip) && (Validator.isIpv4(ip) || Validator.isIpv6(ip));
    }
}
