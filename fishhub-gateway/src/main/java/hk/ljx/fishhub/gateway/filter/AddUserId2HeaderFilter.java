package hk.ljx.fishhub.gateway.filter;

import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static hk.ljx.framework.common.constant.GlobalConstants.USER_ID;


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

        String authorization = exchange.getRequest().getHeaders().getFirst(AUTHORIZATION);
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            String token = authorization.substring(BEARER_PREFIX.length()).trim();
            Object loginId = StpUtil.getLoginIdByToken(token);
            if (loginId != null) {
                requestBuilder.header(USER_ID, loginId.toString());
            }
        }

        ServerWebExchange newExchange = exchange.mutate()
                .request(requestBuilder.build())
                .build();

        return chain.filter(newExchange);
    }
}
