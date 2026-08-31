package hk.ljx.fishhub.user.biz.auth.rpc;

import hk.ljx.framework.common.exception.BizException;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.user.biz.auth.enums.ResponseCodeEnum;
import hk.ljx.fishhub.user.biz.service.UserService;
import hk.ljx.fishhub.user.dto.req.FindUserByPhoneReqDTO;
import hk.ljx.fishhub.user.dto.req.ResolveLoginableUserReqDTO;
import hk.ljx.fishhub.user.dto.req.UpdateUserPasswordReqDTO;
import hk.ljx.fishhub.user.dto.rsp.FindUserByPhoneRspDTO;
import hk.ljx.fishhub.user.dto.rsp.ResolveLoginableUserRspDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;


@Component
@RequiredArgsConstructor
public class UserRpcService {

    private final UserService userService;

    /**
     * 用户注册
     *
     * @param phone
     * @param encodePassword
     * @return
     */
    public Long registerUser(String phone, String encodePassword) {
        Response<Long> response = userService.register(phone, encodePassword);
        if (response == null || !response.isSuccess()) {
            throw new BizException(ResponseCodeEnum.REGISTER_FAIL);
        }
        return response.getData();
    }

    /**
     * 查询手机号对应的可登录账号；不存在时创建默认账号。
     *
     * @param phone
     * @return
     */
    public ResolveLoginableUserRspDTO resolveOrRegisterLoginableUser(String phone) {
        ResolveLoginableUserReqDTO request = new ResolveLoginableUserReqDTO();
        request.setPhone(phone);

        Response<ResolveLoginableUserRspDTO> response = userService.resolveOrRegisterLoginableUser(request);

        if (response == null || !response.isSuccess()) {
            return null;
        }

        return response.getData();
    }

    /**
     * 根据手机号查询用户信息
     *
     * @param phone
     * @return
     */
    public FindUserByPhoneRspDTO findUserByPhone(String phone) {
        FindUserByPhoneReqDTO request = new FindUserByPhoneReqDTO();
        request.setPhone(phone);

        try {
            Response<FindUserByPhoneRspDTO> response = userService.findByPhone(request);
            if (response == null || !response.isSuccess()) {
                return null;
            }
            return response.getData();
        } catch (BizException e) {
            if (Objects.equals(e.getErrorCode(), ResponseCodeEnum.USER_NOT_FOUND.getErrorCode())) {
                return null;
            }
            throw e;
        }
    }

    /**
     * 密码更新
     *
     * @param encodePassword
     */
    public void updatePassword(String encodePassword) {
        UpdateUserPasswordReqDTO request = new UpdateUserPasswordReqDTO();
        request.setEncodePassword(encodePassword);

        Response<Boolean> response = userService.updatePassword(request);
        if (response == null || !response.isSuccess() || !Boolean.TRUE.equals(response.getData())) {
            throw new BizException(ResponseCodeEnum.PASSWORD_UPDATE_FAIL);
        }
    }
}
