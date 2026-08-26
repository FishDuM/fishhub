package hk.ljx.fishhub.user.biz.auth.service.impl;

import hk.ljx.fishhub.user.biz.auth.enums.ResponseCodeEnum;
import hk.ljx.fishhub.user.biz.auth.model.vo.captcha.CaptchaRspVO;
import hk.ljx.fishhub.user.biz.auth.model.vo.user.UserLoginReqVO;
import hk.ljx.fishhub.user.biz.auth.model.vo.user.UserRegisterReqVO;
import hk.ljx.fishhub.user.biz.auth.rpc.UserRpcService;
import hk.ljx.framework.common.exception.BizException;
import hk.ljx.framework.common.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserRpcService userRpcService;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(stringRedisTemplate, passwordEncoder, userRpcService);
    }

    @Test
    void shouldGenerateCaptchaAndSaveToRedis() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        Response<CaptchaRspVO> response = authService.getCaptcha();

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
        assertNotNull(response.getData().getCaptchaKey());
        assertTrue(response.getData().getCaptchaBase64().startsWith("data:image/png;base64,"));

        verify(valueOperations).set(
                startsWith("auth:captcha:"),
                anyString(),
                eq(300L),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    void shouldRejectLoginWhenCaptchaExpiredOrNotFound() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null); // captcha not found / expired

        UserLoginReqVO reqVO = UserLoginReqVO.builder()
                .phone("13800138000")
                .password("123456")
                .captchaKey("key-1")
                .captchaCode("abcd")
                .build();

        BizException ex = assertThrows(BizException.class, () -> authService.login(reqVO));
        assertEquals("AUTH-20000", ex.getErrorCode());
    }

    @Test
    void shouldRejectLoginWhenCaptchaMismatch() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("wxyz"); // mismatch

        UserLoginReqVO reqVO = UserLoginReqVO.builder()
                .phone("13800138000")
                .password("123456")
                .captchaKey("key-1")
                .captchaCode("abcd")
                .build();

        BizException ex = assertThrows(BizException.class, () -> authService.login(reqVO));
        assertEquals("AUTH-20001", ex.getErrorCode());
    }

    @Test
    void shouldRejectRegisterWhenPhoneAlreadyExists() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("abcd"); // captcha pass

        when(userRpcService.registerUser(eq("13800138000"), any()))
                .thenThrow(new BizException(ResponseCodeEnum.PHONE_ALREADY_REGISTERED));

        UserRegisterReqVO reqVO = UserRegisterReqVO.builder()
                .phone("13800138000")
                .password("123456")
                .captchaKey("key-1")
                .captchaCode("abcd")
                .build();

        BizException ex = assertThrows(BizException.class, () -> authService.register(reqVO));
        assertEquals("AUTH-20003", ex.getErrorCode());
        verify(stringRedisTemplate).delete(anyString());
    }
}
