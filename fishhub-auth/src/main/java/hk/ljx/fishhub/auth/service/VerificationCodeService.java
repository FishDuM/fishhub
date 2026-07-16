package hk.ljx.fishhub.auth.service;

import hk.ljx.fishhub.auth.modal.vo.verificationcode.SendVerificationCodeReqVO;
import hk.ljx.framework.common.response.Response;

public interface VerificationCodeService {

    /**
     * 发送短信验证码
     *
     * @param sendVerificationCodeReqVO
     * @return
     */
    Response<?> send(SendVerificationCodeReqVO sendVerificationCodeReqVO);
}