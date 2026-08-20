package hk.ljx.fishhub.gateway.filter;

import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import org.apache.commons.lang3.StringUtils;

import static hk.ljx.framework.common.constant.GlobalConstants.USER_ID;
import static hk.ljx.fishhub.gateway.auth.SaTokenConfigure.USER_ID_ATTR;


@Component
@Slf4j
@Order(-90)
public class AddUserId2HeaderFilter implements GlobalFilter {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 不信任客户端自行携带的 userId，请求身份只能由网关确认后写入。
        ServerHttpRequest.Builder requestBuilder = exchange.getRequest()
                .mutate()
                .headers(headers -> headers.remove(USER_ID));

        // 优先读取 setAuth 已写入 attribute 的 loginId，省一次 token 解析（Redis 读）。
        Object cachedLoginId = exchange.getAttribute(USER_ID_ATTR);
        if (cachedLoginId != null) {
            requestBuilder.header(USER_ID, cachedLoginId.toString());
        } else {
            // 免登录路径回退为按 token 解析。
            String authorization = exchange.getRequest().getHeaders().getFirst(AUTHORIZATION);
            if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
                String token = authorization.substring(BEARER_PREFIX.length()).trim();
                Object loginId = StpUtil.getLoginIdByToken(token);
                if (loginId != null) {
                    requestBuilder.header(USER_ID, loginId.toString());
                }
            }
        }

        // 注入真实客户端 IP 到 X-Real-IP 头（兼容 Nginx 反向代理）
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String clientIp = headers == null ? null : headers.getFirst("X-Real-IP");
        if (StringUtils.isBlank(clientIp) && headers != null) {
            String forwarded = headers.getFirst("X-Forwarded-For");
            if (StringUtils.isNotBlank(forwarded)) {
                clientIp = forwarded.split(",")[0].trim();
            }
        }
        if (StringUtils.isBlank(clientIp) && exchange.getRequest().getRemoteAddress() != null
                && exchange.getRequest().getRemoteAddress().getAddress() != null) {
            clientIp = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        if (StringUtils.isNotBlank(clientIp)) {
            requestBuilder.header("X-Real-IP", clientIp);
        }

        ServerWebExchange newExchange = exchange.mutate()
                .request(requestBuilder.build())
                .build();

        return chain.filter(newExchange);
    }
}
