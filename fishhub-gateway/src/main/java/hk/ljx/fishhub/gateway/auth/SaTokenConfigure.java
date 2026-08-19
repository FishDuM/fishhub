package hk.ljx.fishhub.gateway.auth;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
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
            "/auth/login",
            "/auth/verification/code/send",
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

    public static boolean isWhitelisted(String path) {
        return LOGIN_WHITELIST_PATHS.contains(path);
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
    public SaReactorFilter getSaReactorFilter() {
        return new SaReactorFilter()
                // 拦截地址
                .addInclude("/**")    /* 拦截全部path */
                // 鉴权方法：每次访问进入
                .setAuth(obj -> {
                    log.info("==================> SaReactorFilter, Path: {}", SaHolder.getRequest().getRequestPath());
                    // 登录校验：非白名单路径只解析一次 token，并透传 loginId 到 exchange attribute
                    if (!isWhitelisted(SaHolder.getRequest().getRequestPath())) {
                        Object loginId = StpUtil.getLoginIdDefaultNull();
                        if (loginId == null) {
                            throw new NotLoginException("未登录", NotLoginException.NOT_TOKEN, null);
                        }
                        ServerWebExchange exchange = SaReactorSyncHolder.getContext();
                        if (exchange != null) {
                            putLoginIdAttribute(exchange, loginId);
                        }
                    }

                    // 对外业务接口按权限校验。
                    SaRouter.match("/note/note/publish",
                            r -> StpUtil.checkPermission("app:note:publish"));
                    SaRouter.match("/comment/comment/publish",
                            r -> StpUtil.checkPermission("app:comment:publish"));

                    // 以下接口仅供服务间 Feign 直连调用，不允许经 Gateway 对外访问。
                    SaRouter.match("/user/user/resolve-loginable", r -> StpUtil.checkPermission("internal:service"));
                    SaRouter.match("/user/user/findByPhone", r -> StpUtil.checkPermission("internal:service"));
                    SaRouter.match("/user/user/password/update", r -> StpUtil.checkPermission("internal:service"));
                    SaRouter.match("/user/user/findById", r -> StpUtil.checkPermission("internal:service"));
                    SaRouter.match("/user/user/findByIds", r -> StpUtil.checkPermission("internal:service"));
                    SaRouter.match("/user/user/findRoleAndPermissions", r -> StpUtil.checkPermission("internal:service"));
                    SaRouter.match("/note/note/exists", r -> StpUtil.checkPermission("internal:service"));
                    SaRouter.match("/note/note/accessible", r -> StpUtil.checkPermission("internal:service"));
                    SaRouter.match("/note/note/accessible/batch", r -> StpUtil.checkPermission("internal:service"));
                    SaRouter.match("/note/note/writable/batch", r -> StpUtil.checkPermission("internal:service"));
                    SaRouter.match("/count/count/notes/data", r -> StpUtil.checkPermission("internal:service"));
                    SaRouter.match("/oss/file/delete", r -> StpUtil.checkPermission("internal:service"));
                    SaRouter.match("/search/search/note/document/rebuild",
                            r -> StpUtil.checkPermission("internal:service"));
                    SaRouter.match("/search/search/user/document/rebuild",
                            r -> StpUtil.checkPermission("internal:service"));
                })
                // 异常处理方法：每次setAuth函数出现异常时进入
                .setError(e -> {
                    // 手动抛出异常，抛给全局异常处理器
                    if (e instanceof NotLoginException) {
                        throw new NotLoginException(e.getMessage(), null, null);
                    } else if (e instanceof NotPermissionException || e instanceof NotRoleException) {
                        throw new NotPermissionException(e.getMessage());
                    } else {
                        throw new RuntimeException(e.getMessage());
                    }
                })
                ;
    }
}
