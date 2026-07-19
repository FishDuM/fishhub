package hk.ljx.fishhub.framework.biz.context.interceptor;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import hk.ljx.fishhub.framework.biz.context.holder.LoginUserContextHolder;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

import static hk.ljx.framework.common.constant.GlobalConstants.USER_ID;

@Slf4j
public class FeignRequestInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate requestTemplate) {
        Long userId = LoginUserContextHolder.getUserId();
        if (Objects.nonNull(userId)) {
            requestTemplate.header(USER_ID, userId.toString());
            log.info("设置 Feign 请求头 userId: {}", userId);
        }
    }
}
