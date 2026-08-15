package hk.ljx.fishhub.gateway.auth;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@Slf4j
public class SaTokenConfigure {
    // 注册 Sa-Token全局过滤器
    @Bean
    public SaReactorFilter getSaReactorFilter() {
        return new SaReactorFilter()
                // 拦截地址
                .addInclude("/**")    /* 拦截全部path */
                // 鉴权方法：每次访问进入
                .setAuth(obj -> {
                    log.info("==================> SaReactorFilter, Path: {}", SaHolder.getRequest().getRequestPath());
                    // 登录校验
                    SaRouter.match("/**") // 拦截所有路由
                            .notMatch("/auth/login") // 排除登录接口
                            .notMatch("/auth/verification/code/send") // 排除验证码发送接口
                            .notMatch("/user/user/profile") // 排除用户主页查看
                            .notMatch("/note/channel/list") // 排除发现页频道标签接口
                            .notMatch("/note/discover/note/list") // 排除发现页瀑布流接口
                            .notMatch("/note/note/detail") // 排除笔记详情读取接口
                            .notMatch("/note/note/published/list") // 排除个人主页已发布笔记接口
                            .notMatch("/comment/comment/list") // 排除评论读取接口
                            .notMatch("/comment/comment/child/list") // 排除子评论读取接口
                            .notMatch("/relation/relation/following/list") // 排除关注列表读取接口
                            .notMatch("/relation/relation/fans/list") // 排除粉丝列表读取接口
                            .notMatch("/search/search/note") // 排除笔记搜索接口
                            .notMatch("/search/search/user") // 排除用户搜索接口
                            .check(r -> StpUtil.checkLogin()) // 校验是否登录
                    ;

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
                    SaRouter.match("/note/note/exists", r -> StpUtil.checkPermission("internal:service"));
                    SaRouter.match("/note/note/accessible", r -> StpUtil.checkPermission("internal:service"));
                    SaRouter.match("/note/note/accessible/batch", r -> StpUtil.checkPermission("internal:service"));
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
