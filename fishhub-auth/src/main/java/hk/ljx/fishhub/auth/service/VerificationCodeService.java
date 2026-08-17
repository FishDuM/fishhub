package hk.ljx.fishhub.auth.service;

import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.auth.model.vo.verificationcode.SendVerificationCodeReqVO;


public interface VerificationCodeService {

    /**
     * 发送短信验证码
     *
     * @param sendVerificationCodeReqVO 请求参数
     * @param clientIp                 客户端 IP；为空时跳过 IP 限流
     * @return
     */
    Response<?> send(SendVerificationCodeReqVO sendVerificationCodeReqVO, String clientIp);
}
