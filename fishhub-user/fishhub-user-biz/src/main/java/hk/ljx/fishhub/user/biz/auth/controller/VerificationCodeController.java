package hk.ljx.fishhub.user.biz.auth.controller;

import hk.ljx.framework.biz.operationlog.aspect.ApiOperationLog;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.user.biz.auth.model.vo.verificationcode.SendVerificationCodeReqVO;
import hk.ljx.fishhub.user.biz.auth.service.VerificationCodeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


@RestController
@Slf4j
@RequiredArgsConstructor
public class VerificationCodeController {

    private final VerificationCodeService verificationCodeService;

    @PostMapping("/verification/code/send")
    @ApiOperationLog(description = "发送短信验证码")
    public Response<?> send(@Validated @RequestBody SendVerificationCodeReqVO sendVerificationCodeReqVO,
                            HttpServletRequest request) {
        return verificationCodeService.send(sendVerificationCodeReqVO, resolveClientIp(request));
    }

    /**
     * 解析客户端 IP
     */
    private String resolveClientIp(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.isNotBlank(realIp)) {
            return realIp.trim();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.isNotBlank(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

}
