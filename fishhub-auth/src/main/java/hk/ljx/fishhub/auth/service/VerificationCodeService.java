package hk.ljx.fishhub.auth.service;

import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.auth.model.vo.verificationcode.SendVerificationCodeReqVO;


public interface VerificationCodeService {

    /**
     * 发送短信验证码
     *
     * @param sendVerificationCodeReqVO
     * @return
     */
    Response<?> send(SendVerificationCodeReqVO sendVerificationCodeReqVO);
}
