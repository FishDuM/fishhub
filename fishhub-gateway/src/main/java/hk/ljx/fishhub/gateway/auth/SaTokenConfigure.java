package hk.ljx.fishhub.gateway.auth;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;

import java.util.Set;

import static hk.ljx.framework.common.constant.GlobalConstants.USER_ID;


@Configuration
@Slf4j
public class SaTokenConfigure {

    /**
     * Exchange attribute：setAuth 写入的 loginId，供 AddUserId2HeaderFilter 直接读取。
     */
    public static final String USER_ID_ATTR = "fishhub:" + USER_ID;

    /**
     * 免登录白名单
     */
    private static final Set<String> LOGIN_WHITELIST_PATHS = Set.of(
            "/auth/captcha",
            "/auth/login",
            "/auth/register",
            "/user/user/profile",
            "/note/channel/list",
            "/note/topic/list",
            "/note/discover/note/list",
            "/note/note/detail",
            "/note/note/published/list",
            "/comment/comment/list",
            "/comment/comment/child/list",
            "/relation/relation/following/list",
            "/relation/relation/fans/list",
            "/search/search/note",
            "/search/search/user");

    /**
     * 仅供微服务间 Feign 内部直连调用的接口，禁止通过网关对外暴露
     */
    private static final Set<String> INTERNAL_SERVICE_PATHS = Set.of(
            "/user/user/resolve-loginable",
            "/user/user/findByPhone",
            "/user/user/password/update",
            "/user/user/findById",
            "/user/user/findActiveById",
            "/user/user/findByIds",
            "/note/note/exists",
            "/note/note/accessible",
            "/note/note/accessible/batch",
            "/note/note/writable/batch",
            "/oss/file/delete",
            "/search/search/note/document/rebuild",
            "/search/search/user/document/rebuild");

    public static boolean isWhitelisted(String path) {
        return LOGIN_WHITELIST_PATHS.contains(path);
    }

    public static boolean isInternalPath(String path) {
        return INTERNAL_SERVICE_PATHS.contains(path);
    }

    /**
     * 将 loginId 写入 exchange attribute。
     */
    public static void putLoginIdAttribute(ServerWebExchange exchange, Object loginId) {
        if (loginId != null) {
            exchange.getAttributes().put(USER_ID_ATTR, String.valueOf(loginId));
        }
    }

    // 注册 Sa-Token全局过滤器
    @Bean
    public SaReactorFilter saReactorFilter() {
        return new SaReactorFilter()
                // 拦截地址
                .addInclude("/**")    /* 拦截全部path */
                // 鉴权方法：每次访问进入
                .setAuth(obj -> {
                    String requestPath = SaHolder.getRequest().getRequestPath();
                    log.debug("==================> SaReactorFilter, Path: {}", requestPath);

                    // 1. 内部接口拦截：禁止通过网关访问微服务间 RPC 接口
                    if (isInternalPath(requestPath)) {
                        throw new NotPermissionException("该接口仅供内部微服务调用，禁止外部访问", "internal:service");
                    }

                    // 2. 登录校验：非白名单路径校验 token，并透传 loginId 到 exchange attribute
                    if (!isWhitelisted(requestPath)) {
                        Object loginId = StpUtil.getLoginIdDefaultNull();
                        if (loginId == null) {
                            throw new NotLoginException("未登录", NotLoginException.NOT_TOKEN, null);
                        }
                        ServerWebExchange exchange = SaReactorSyncHolder.getContext();
                        if (exchange != null) {
                            putLoginIdAttribute(exchange, loginId);
                        }
                    }
                })
                // 异常处理方法：每次setAuth函数出现异常时进入
                .setError(e -> {
                    if (e instanceof RuntimeException re) {
                        throw re;
                    }
                    throw new RuntimeException(e);
                })
                ;
    }
}
