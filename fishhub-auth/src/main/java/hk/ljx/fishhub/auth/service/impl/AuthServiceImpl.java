package hk.ljx.fishhub.auth.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.google.common.base.Preconditions;
import hk.ljx.framework.biz.context.holder.LoginUserContextHolder;
import hk.ljx.framework.common.exception.BizException;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.auth.constant.RedisKeyConstants;
import hk.ljx.fishhub.auth.enums.LoginTypeEnum;
import hk.ljx.fishhub.auth.enums.ResponseCodeEnum;
import hk.ljx.fishhub.auth.model.vo.user.UpdatePasswordReqVO;
import hk.ljx.fishhub.auth.model.vo.user.UserLoginReqVO;
import hk.ljx.fishhub.auth.rpc.UserRpcService;
import hk.ljx.fishhub.auth.service.AuthService;
import hk.ljx.fishhub.user.dto.resp.FindUserByPhoneRspDTO;
import hk.ljx.fishhub.user.dto.resp.ResolveLoginableUserRspDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;


@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    private static final DefaultRedisScript<Long> VERIFY_AND_CONSUME_CODE_SCRIPT = new DefaultRedisScript<>(
            "local value = redis.call('get', KEYS[1]); "
                    + "if value == ARGV[1] then redis.call('del', KEYS[1]); return 1; end; "
                    + "return 0;", Long.class);

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private PasswordEncoder passwordEncoder;
    @Resource
    private UserRpcService userRpcService;

    /**
     * 登录与注册
     *
     * @param userLoginReqVO
     * @return
     */
    @Override
    public Response<String> loginAndRegister(UserLoginReqVO userLoginReqVO) {
        String phone = userLoginReqVO.getPhone();
        Integer type = userLoginReqVO.getType();

        LoginTypeEnum loginTypeEnum = LoginTypeEnum.valueOf(type);

        if (Objects.isNull(loginTypeEnum)) {
            throw new BizException(ResponseCodeEnum.LOGIN_TYPE_ERROR);
        }

        Long userId = null;

        switch (loginTypeEnum) {
            case VERIFICATION_CODE: // 验证码登录
                String verificationCode = userLoginReqVO.getCode();

                Preconditions.checkArgument(StringUtils.isNotBlank(verificationCode), "验证码不能为空");

                verifyAndConsumeVerificationCode(phone, verificationCode);

                ResolveLoginableUserRspDTO resolvedUser = userRpcService.resolveOrRegisterLoginableUser(phone);

                if (Objects.isNull(resolvedUser)) {
                    throw new BizException(ResponseCodeEnum.LOGIN_FAIL);
                }
                if (!resolvedUser.isLoginable()) {
                    throw new BizException(ResponseCodeEnum.ACCOUNT_NOT_LOGINABLE);
                }

                userId = resolvedUser.getUserId();
                break;
            case PASSWORD: // 密码登录
                String password = userLoginReqVO.getPassword();

                FindUserByPhoneRspDTO findUserByPhoneRspDTO = userRpcService.findUserByPhone(phone);

                if (Objects.isNull(findUserByPhoneRspDTO)) {
                    throw new BizException(ResponseCodeEnum.USER_NOT_FOUND);
                }

                String encodePassword = findUserByPhoneRspDTO.getPassword();

                boolean isPasswordCorrect = StringUtils.isNotBlank(encodePassword)
                        && passwordEncoder.matches(password, encodePassword);

                if (!isPasswordCorrect) {
                    throw new BizException(ResponseCodeEnum.PHONE_OR_PASSWORD_ERROR);
                }

                userId = findUserByPhoneRspDTO.getId();
                break;
            default:
                break;
        }

        StpUtil.login(userId);

        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        return Response.success(tokenInfo.tokenValue);
    }

    /**
     * 退出登录
     *
     * @return
     */
    @Override
    public Response<?> logout() {
        Long userId = LoginUserContextHolder.getUserId();

        StpUtil.logout(userId);

        return Response.success();
    }

    /**
     * 修改密码
     *
     * @param updatePasswordReqVO
     * @return
     */
    @Override
    public Response<?> updatePassword(UpdatePasswordReqVO updatePasswordReqVO) {
        String phone = updatePasswordReqVO.getPhone();
        String verificationCode = updatePasswordReqVO.getCode();

        Preconditions.checkArgument(StringUtils.isNotBlank(verificationCode), "验证码不能为空");

        FindUserByPhoneRspDTO user = userRpcService.findUserByPhone(phone);
        if (user == null || !Objects.equals(user.getId(), LoginUserContextHolder.getUserId())) {
            throw new BizException(ResponseCodeEnum.USER_NOT_FOUND);
        }

        verifyAndConsumeVerificationCode(phone, verificationCode);

        String newPassword = updatePasswordReqVO.getNewPassword();
        String encodePassword = passwordEncoder.encode(newPassword);

        userRpcService.updatePassword(encodePassword);

        StpUtil.logout(LoginUserContextHolder.getUserId());

        return Response.success();
    }

    private void verifyAndConsumeVerificationCode(String phone, String verificationCode) {
        Long consumed = stringRedisTemplate.execute(VERIFY_AND_CONSUME_CODE_SCRIPT,
                List.of(RedisKeyConstants.buildVerificationCodeKey(phone)), verificationCode);
        if (!Long.valueOf(1L).equals(consumed)) {
            throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_ERROR);
        }
    }

}
