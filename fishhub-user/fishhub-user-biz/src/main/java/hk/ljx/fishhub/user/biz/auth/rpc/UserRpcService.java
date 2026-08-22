package hk.ljx.fishhub.user.biz.auth.rpc;

import hk.ljx.framework.common.exception.BizException;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.user.biz.auth.enums.ResponseCodeEnum;
import hk.ljx.fishhub.user.biz.service.RolePermissionService;
import hk.ljx.fishhub.user.biz.service.UserService;
import hk.ljx.fishhub.user.dto.req.FindUserByPhoneReqDTO;
import hk.ljx.fishhub.user.dto.req.ResolveLoginableUserReqDTO;
import hk.ljx.fishhub.user.dto.req.UpdateUserPasswordReqDTO;
import hk.ljx.fishhub.user.dto.rsp.FindUserByPhoneRspDTO;
import hk.ljx.fishhub.user.dto.rsp.ResolveLoginableUserRspDTO;
import hk.ljx.fishhub.user.dto.rsp.UserRolePermissionRspDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class UserRpcService {

    private final UserService userService;
    private final RolePermissionService rolePermissionService;

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

        Response<FindUserByPhoneRspDTO> response = userService.findByPhone(request);

        if (response == null || !response.isSuccess()) {
            return null;
        }

        return response.getData();
    }

    /**
     * 查询用户角色与权限
     *
     * @param userId
     * @return
     */
    public UserRolePermissionRspDTO findRoleAndPermissions(Long userId) {
        return rolePermissionService.findByUserId(userId);
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
