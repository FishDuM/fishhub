package hk.ljx.fishhub.user.biz.auth.service.impl;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.google.common.base.Preconditions;
import hk.ljx.framework.biz.context.holder.LoginUserContextHolder;
import hk.ljx.framework.common.exception.BizException;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.user.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.user.biz.auth.enums.LoginTypeEnum;
import hk.ljx.fishhub.user.biz.auth.enums.ResponseCodeEnum;
import hk.ljx.fishhub.user.biz.auth.model.vo.user.UpdatePasswordReqVO;
import hk.ljx.fishhub.user.biz.auth.model.vo.user.UserLoginReqVO;
import hk.ljx.fishhub.user.biz.auth.rpc.UserRpcService;
import hk.ljx.fishhub.user.biz.auth.service.AuthService;
import hk.ljx.fishhub.user.dto.rsp.FindUserByPhoneRspDTO;
import hk.ljx.fishhub.user.dto.rsp.ResolveLoginableUserRspDTO;
import hk.ljx.fishhub.user.dto.rsp.UserRolePermissionRspDTO;
import hk.ljx.framework.common.util.RedisScriptHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final DefaultRedisScript<Long> VERIFY_AND_CONSUME_CODE_SCRIPT = RedisScriptHelper.loadLongScript("/lua/verify_and_consume_code.lua");

    /** 验证码最大校验失败次数，超过即作废，防止 6 位数字验证码在 TTL 内被穷举 */
    private static final int VERIFICATION_CODE_MAX_ATTEMPTS = 5;

    /** 登录接口限流：同一手机号每分钟最大尝试次数 */
    private static final int LOGIN_PHONE_RATE_LIMIT_PER_MINUTE = 10;

    /** 登录接口限流：同一 IP 每分钟最大尝试次数 */
    private static final int LOGIN_IP_RATE_LIMIT_PER_MINUTE = 60;

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = RedisScriptHelper.loadLongScript("/lua/rate_limit.lua");

    private final StringRedisTemplate stringRedisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final UserRpcService userRpcService;

    /**
     * 登录与注册
     *
     * @param userLoginReqVO
     * @return
     */
    @Override
    public Response<String> loginAndRegister(UserLoginReqVO userLoginReqVO, String clientIp) {
        String phone = userLoginReqVO.getPhone();
        Integer type = userLoginReqVO.getType();

        // 登录接口双维度分钟限流：手机号/IP 任一超限即拒绝，防止密码/验证码被暴力尝试
        checkLoginRateLimit(phone, clientIp);

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

        // 登录时装配角色权限并写入会话，网关鉴权从会话读取，不再维护独立的角色权限缓存。
        UserRolePermissionRspDTO rolePermission = userRpcService.findRoleAndPermissions(userId);
        if (rolePermission == null) {
            log.warn("获取用户角色权限失败，本次登录将无角色权限，userId={}", userId);
        }
        SaSession session = StpUtil.getSession();
        session.set(SaSession.ROLE_LIST, rolePermission == null || rolePermission.getRoles() == null
                ? Collections.emptyList() : rolePermission.getRoles());
        session.set(SaSession.PERMISSION_LIST, rolePermission == null || rolePermission.getPermissions() == null
                ? Collections.emptyList() : rolePermission.getPermissions());

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
        String key = RedisKeyConstants.buildVerificationCodeKey(phone);
        Long result = stringRedisTemplate.execute(VERIFY_AND_CONSUME_CODE_SCRIPT,
                Collections.singletonList(key), verificationCode, String.valueOf(VERIFICATION_CODE_MAX_ATTEMPTS));
        if (Objects.equals(result, 1L)) {
            return;
        }
        if (Objects.equals(result, -2L)) {
            throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_TOO_MANY_ATTEMPTS);
        }
        throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_ERROR);
    }

    /**
     * 登录接口双维度分钟限流（固定窗口计数，与发送验证码限流同构）。
     * 限流键独立于发送验证码的限流键，避免两类接口互相挤占额度。
     */
    private void checkLoginRateLimit(String phone, String clientIp) {
        Long phoneCount = stringRedisTemplate.execute(RATE_LIMIT_SCRIPT,
                Collections.singletonList(RedisKeyConstants.buildLoginPhoneRateLimitKey(phone)), String.valueOf(60));
        if (phoneCount != null && phoneCount > LOGIN_PHONE_RATE_LIMIT_PER_MINUTE) {
            throw new BizException(ResponseCodeEnum.LOGIN_TOO_FREQUENT);
        }
        if (StringUtils.isNotBlank(clientIp)) {
            Long ipCount = stringRedisTemplate.execute(RATE_LIMIT_SCRIPT,
                    Collections.singletonList(RedisKeyConstants.buildLoginIpRateLimitKey(clientIp)), String.valueOf(60));
            if (ipCount != null && ipCount > LOGIN_IP_RATE_LIMIT_PER_MINUTE) {
                throw new BizException(ResponseCodeEnum.LOGIN_TOO_FREQUENT);
            }
        }
    }

}
