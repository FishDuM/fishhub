package hk.ljx.fishhub.auth.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.google.common.base.Preconditions;
import hk.ljx.fishhub.auth.constant.RedisKeyConstants;
import hk.ljx.fishhub.auth.enums.LoginTypeEnum;
import hk.ljx.fishhub.auth.enums.ResponseCodeEnum;
import hk.ljx.fishhub.auth.modal.vo.user.UpdatePasswordReqVO;
import hk.ljx.fishhub.auth.modal.vo.user.UserLoginReqVO;
import hk.ljx.fishhub.auth.rpc.UserRpcService;
import hk.ljx.fishhub.auth.service.AuthService;
import hk.ljx.fishhub.framework.biz.context.holder.LoginUserContextHolder;
import hk.ljx.fishhub.user.dto.resp.FindUserByPhoneRspDTO;
import hk.ljx.framework.common.exception.BizException;
import hk.ljx.framework.common.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static hk.ljx.fishhub.auth.enums.ResponseCodeEnum.LOGIN_TYPE_ERROR;
import static hk.ljx.fishhub.auth.enums.ResponseCodeEnum.USER_NOT_FOUND;

@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private UserRpcService userRpcService;

    @Override
    public Response<String> loginAndRegister(UserLoginReqVO userLoginReqVO) {
        String phone = userLoginReqVO.getPhone();
        Integer type = userLoginReqVO.getType();
        Long userId = null;

        LoginTypeEnum loginTypeEnum = LoginTypeEnum.valueOf(type);
        switch (loginTypeEnum) {
            case VERIFICATION_CODE: // 验证码登录
                String verificationCode = userLoginReqVO.getCode();
                Preconditions.checkArgument(StringUtils.isNotBlank(verificationCode), "验证码不能为空");
                // 校验验证码
                String key = RedisKeyConstants.buildVerificationCodeKey(phone);
                String sentCode = (String) redisTemplate.opsForValue().get(key);
                if (!StringUtils.equals(verificationCode, sentCode)) {
                    throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_ERROR);
                }
                // RPC: 调用用户服务，注册用户
                Long userIdTmp = userRpcService.registerUser(phone);

                // 若调用用户服务，返回的用户 ID 为空，则提示登录失败
                if (Objects.isNull(userIdTmp)) {
                    throw new BizException(ResponseCodeEnum.LOGIN_FAIL);
                }
                userId = userIdTmp;
                break;
            case PASSWORD:
                String password = userLoginReqVO.getPassword();

                FindUserByPhoneRspDTO findUserByPhoneRspDTO = userRpcService.findUserByPhone(phone);
                // 若用户不存在，则提示登录失败
                if (Objects.isNull(findUserByPhoneRspDTO)){
                    throw new BizException(USER_NOT_FOUND);
                }
                String encodePassword = findUserByPhoneRspDTO.getPassword();
                // 匹配密码是否一致
                boolean isPasswordCorrect = passwordEncoder.matches(password, encodePassword);
                // 如果不正确，则抛出业务异常，提示用户名或者密码不正确
                if (!isPasswordCorrect) {
                    throw new BizException(ResponseCodeEnum.PHONE_OR_PASSWORD_ERROR);
                }
                userId = findUserByPhoneRspDTO.getId();
                break;
            default:
                throw new BizException(LOGIN_TYPE_ERROR);
        }

        // SaToken 登录用户, 入参为用户 ID
        StpUtil.login(userId);
        // 获取 Token 令牌
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        // 返回 Token 令牌
        return Response.success(tokenInfo.tokenValue);
    }

    @Override
    public Response<?> logout() {
        Long userId = LoginUserContextHolder.getUserId();
        log.info("== 用户退出登录 ID: {}", userId);
        StpUtil.logout(userId);
        return Response.success();
    }

    @Override
    public Response<?> updatePassword(UpdatePasswordReqVO updatePasswordReqVO) {
        String newPassword = updatePasswordReqVO.getNewPassword();
        String encodedPassword = passwordEncoder.encode(newPassword);
        userRpcService.updatePassword(encodedPassword);
        return Response.success();
    }
}
