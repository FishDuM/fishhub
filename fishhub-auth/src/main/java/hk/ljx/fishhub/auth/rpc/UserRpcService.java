package hk.ljx.fishhub.auth.rpc;

import hk.ljx.framework.common.response.Response;
import hk.ljx.framework.common.exception.BizException;
import hk.ljx.fishhub.auth.enums.ResponseCodeEnum;
import hk.ljx.fishhub.user.api.UserFeignApi;
import hk.ljx.fishhub.user.dto.req.FindUserByPhoneReqDTO;
import hk.ljx.fishhub.user.dto.req.ResolveLoginableUserReqDTO;
import hk.ljx.fishhub.user.dto.req.UpdateUserPasswordReqDTO;
import hk.ljx.fishhub.user.dto.resp.FindUserByPhoneRspDTO;
import hk.ljx.fishhub.user.dto.resp.ResolveLoginableUserRspDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;


@Component
public class UserRpcService {

    @Resource
    private UserFeignApi userFeignApi;

    /**
     * 查询手机号对应的可登录账号；不存在时创建默认账号。
     *
     * @param phone
     * @return
     */
    public ResolveLoginableUserRspDTO resolveOrRegisterLoginableUser(String phone) {
        ResolveLoginableUserReqDTO request = new ResolveLoginableUserReqDTO();
        request.setPhone(phone);

        Response<ResolveLoginableUserRspDTO> response = userFeignApi.resolveOrRegisterLoginableUser(request);

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
        FindUserByPhoneReqDTO findUserByPhoneReqDTO = new FindUserByPhoneReqDTO();
        findUserByPhoneReqDTO.setPhone(phone);

        Response<FindUserByPhoneRspDTO> response = userFeignApi.findByPhone(findUserByPhoneReqDTO);

        if (response == null || !response.isSuccess()) {
            return null;
        }

        return response.getData();
    }

    /**
     * 密码更新
     *
     * @param encodePassword
     */
    public void updatePassword(String encodePassword) {
        UpdateUserPasswordReqDTO updateUserPasswordReqDTO = new UpdateUserPasswordReqDTO();
        updateUserPasswordReqDTO.setEncodePassword(encodePassword);

        Response<Boolean> response = userFeignApi.updatePassword(updateUserPasswordReqDTO);
        if (response == null || !response.isSuccess() || !Boolean.TRUE.equals(response.getData())) {
            throw new BizException(ResponseCodeEnum.PASSWORD_UPDATE_FAIL);
        }
    }

}
