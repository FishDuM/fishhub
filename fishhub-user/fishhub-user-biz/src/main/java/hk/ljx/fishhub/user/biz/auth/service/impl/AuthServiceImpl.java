package hk.ljx.fishhub.user.biz.auth.service.impl;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.core.util.IdUtil;
import com.google.common.base.Preconditions;
import hk.ljx.framework.biz.context.holder.LoginUserContextHolder;
import hk.ljx.framework.common.exception.BizException;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.user.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.user.biz.auth.enums.ResponseCodeEnum;
import hk.ljx.fishhub.user.biz.auth.model.vo.captcha.CaptchaRspVO;
import hk.ljx.fishhub.user.biz.auth.model.vo.user.UpdatePasswordReqVO;
import hk.ljx.fishhub.user.biz.auth.model.vo.user.UserLoginReqVO;
import hk.ljx.fishhub.user.biz.auth.model.vo.user.UserRegisterReqVO;
import hk.ljx.fishhub.user.biz.auth.rpc.UserRpcService;
import hk.ljx.fishhub.user.biz.auth.service.AuthService;
import hk.ljx.fishhub.user.dto.rsp.FindUserByPhoneRspDTO;
import hk.ljx.fishhub.user.dto.rsp.UserRolePermissionRspDTO;
import hk.ljx.framework.common.util.RedisScriptHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final DefaultRedisScript<Long> VERIFY_AND_CONSUME_CAPTCHA_SCRIPT =
            RedisScriptHelper.loadLongScript("/lua/verify_and_consume_captcha.lua");

    /** 图形验证码有效时长：5 分钟 */
    private static final long CAPTCHA_EXPIRE_SECONDS = 300L;

    /** 图形验证码最大连续错误次数，超过10次旧验证码自动失效作废 */
    private static final int CAPTCHA_MAX_ATTEMPTS = 10;

    private final StringRedisTemplate stringRedisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final UserRpcService userRpcService;

    /**
     * 获取图形验证码 (Hutool 生成，5分钟有效)
     */
    @Override
    public Response<CaptchaRspVO> getCaptcha() {
        // 使用 Hutool 创建 4 位干扰线验证码 (宽 120, 高 40, 4 位字符, 10 条干扰线)
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(120, 40, 4, 10);
        String code = captcha.getCode().toLowerCase();
        String captchaKey = IdUtil.fastSimpleUUID();

        // 写入 Redis，TTL 为 5 分钟 (300 秒)
        String redisKey = RedisKeyConstants.buildCaptchaKey(captchaKey);
        stringRedisTemplate.opsForValue().set(redisKey, code, CAPTCHA_EXPIRE_SECONDS, TimeUnit.SECONDS);

        return Response.success(CaptchaRspVO.builder()
                .captchaKey(captchaKey)
                .captchaBase64(captcha.getImageBase64Data())
                .build());
    }

    /**
     * 用户注册
     *
     * @param userRegisterReqVO
     * @return
     */
    @Override
    public Response<String> register(UserRegisterReqVO userRegisterReqVO) {
        String phone = userRegisterReqVO.getPhone();
        String password = userRegisterReqVO.getPassword();
        String captchaKey = userRegisterReqVO.getCaptchaKey();
        String captchaCode = userRegisterReqVO.getCaptchaCode();

        verifyAndConsumeCaptcha(captchaKey, captchaCode);

        String encodePassword = passwordEncoder.encode(password);
        Long userId = userRpcService.registerUser(phone, encodePassword);

        return performLogin(userId);
    }

    /**
     * 用户登录
     *
     * @param userLoginReqVO
     * @return
     */
    @Override
    public Response<String> login(UserLoginReqVO userLoginReqVO) {
        String phone = userLoginReqVO.getPhone();
        String password = userLoginReqVO.getPassword();
        String captchaKey = userLoginReqVO.getCaptchaKey();
        String captchaCode = userLoginReqVO.getCaptchaCode();

        verifyAndConsumeCaptcha(captchaKey, captchaCode);

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

        Long userId = findUserByPhoneRspDTO.getId();
        return performLogin(userId);
    }

    /**
     * 退出登录
     *
     * @return
     */
    @Override
    public Response<?> logout() {
        Long userId = LoginUserContextHolder.getUserId();
        if (userId != null) {
            StpUtil.logout(userId);
        }
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

        FindUserByPhoneRspDTO user = userRpcService.findUserByPhone(phone);
        if (user == null || !Objects.equals(user.getId(), LoginUserContextHolder.getUserId())) {
            throw new BizException(ResponseCodeEnum.USER_NOT_FOUND);
        }

        String newPassword = updatePasswordReqVO.getNewPassword();
        String encodePassword = passwordEncoder.encode(newPassword);

        userRpcService.updatePassword(encodePassword);

        StpUtil.logout(LoginUserContextHolder.getUserId());

        return Response.success();
    }

    /**
     * 执行 Sa-Token 登录与会话角色权限初始化
     */
    private Response<String> performLogin(Long userId) {
        StpUtil.login(userId);

        UserRolePermissionRspDTO rolePermission = userRpcService.findRoleAndPermissions(userId);
        if (rolePermission == null) {
            log.warn("获取用户角色权限失败，本次登录将无角色权限，userId={}", userId);
        }
        SaSession session = StpUtil.getSession();
        List<String> roles = (rolePermission != null && rolePermission.getRoles() != null)
                ? rolePermission.getRoles() : Collections.emptyList();
        List<String> permissions = (rolePermission != null && rolePermission.getPermissions() != null)
                ? rolePermission.getPermissions() : Collections.emptyList();

        session.set(SaSession.ROLE_LIST, roles);
        session.set(SaSession.PERMISSION_LIST, permissions);

        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        return Response.success(tokenInfo.tokenValue);
    }

    /**
     * 图形验证码原子校验与消费（5分钟有效，错误超10次旧码自动作废）
     */
    private void verifyAndConsumeCaptcha(String captchaKey, String captchaCode) {
        Preconditions.checkArgument(StringUtils.isNotBlank(captchaKey), "验证码标识不能为空");
        Preconditions.checkArgument(StringUtils.isNotBlank(captchaCode), "验证码不能为空");

        String key = RedisKeyConstants.buildCaptchaKey(captchaKey);
        String normalizedCode = captchaCode.trim().toLowerCase();

        Long result = stringRedisTemplate.execute(
                VERIFY_AND_CONSUME_CAPTCHA_SCRIPT,
                Collections.singletonList(key),
                normalizedCode,
                String.valueOf(CAPTCHA_MAX_ATTEMPTS)
        );

        if (Objects.equals(result, 1L)) {
            return; // 校验成功并已删除
        }
        if (Objects.equals(result, -2L)) {
            throw new BizException(ResponseCodeEnum.CAPTCHA_TOO_MANY_ATTEMPTS);
        }
        if (Objects.equals(result, -1L)) {
            throw new BizException(ResponseCodeEnum.CAPTCHA_NOT_FOUND_OR_EXPIRED);
        }
        throw new BizException(ResponseCodeEnum.CAPTCHA_ERROR);
    }
}
