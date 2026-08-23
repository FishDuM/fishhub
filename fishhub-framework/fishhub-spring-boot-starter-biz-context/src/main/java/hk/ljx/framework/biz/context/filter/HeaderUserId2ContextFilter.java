package hk.ljx.framework.biz.context.filter;

import hk.ljx.framework.biz.context.holder.LoginUserContextHolder;
import hk.ljx.framework.common.constant.GlobalConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;


@Slf4j
public class HeaderUserId2ContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            // 从请求头中获取用户 ID
            String userId = request.getHeader(GlobalConstants.USER_ID);

            // 判断请求头中是否存在用户 ID
            if (StringUtils.isNotBlank(userId)) {
                log.debug("===== 设置 userId 到 ThreadLocal 中， 用户 ID: {}", userId);
                LoginUserContextHolder.setUserId(userId);
            }

            chain.doFilter(request, response);
        } finally {
            // 一定要删除 ThreadLocal ，防止内存泄露以及线程复用导致的越权
            LoginUserContextHolder.remove();
        }
    }
}
