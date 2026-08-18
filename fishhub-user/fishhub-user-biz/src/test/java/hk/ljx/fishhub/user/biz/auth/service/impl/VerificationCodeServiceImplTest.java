package hk.ljx.fishhub.user.biz.auth.service.impl;

import hk.ljx.framework.common.exception.BizException;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.user.biz.auth.model.vo.verificationcode.SendVerificationCodeReqVO;
import hk.ljx.fishhub.user.biz.auth.sms.AliyunSmsHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationCodeServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private AliyunSmsHelper aliyunSmsHelper;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private VerificationCodeServiceImpl service;

    private static final SendVerificationCodeReqVO REQUEST =
            SendVerificationCodeReqVO.builder().phone("13800000000").build();
    private static final String PHONE_LIMIT_KEY = "verification_code:minute:phone:13800000000";
    private static final String IP_LIMIT_KEY = "verification_code:minute:ip:1.2.3.4";

    @Test
    void shouldRejectWhenIpLimitExceeded() {
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), eq(List.of(PHONE_LIMIT_KEY)), eq("60"))).thenReturn(1L);
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), eq(List.of(IP_LIMIT_KEY)), eq("60"))).thenReturn(501L);

        assertThrows(BizException.class, () -> service.send(REQUEST, "1.2.3.4"));

        verify(aliyunSmsHelper, never()).sendMessage(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldRejectWhenPhoneLimitExceededBeforeCheckingIp() {
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), eq(List.of(PHONE_LIMIT_KEY)), eq("60"))).thenReturn(101L);

        assertThrows(BizException.class, () -> service.send(REQUEST, "1.2.3.4"));

        verify(stringRedisTemplate, never()).execute(any(DefaultRedisScript.class), eq(List.of(IP_LIMIT_KEY)), eq("60"));
        verify(aliyunSmsHelper, never()).sendMessage(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldSendSmsWhenBothLimitsPass() {
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), eq(List.of(PHONE_LIMIT_KEY)), eq("60"))).thenReturn(1L);
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), eq(List.of(IP_LIMIT_KEY)), eq("60"))).thenReturn(1L);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("verification_code:13800000000"), anyString(), eq(3L), eq(TimeUnit.MINUTES))).thenReturn(true);
        when(aliyunSmsHelper.sendMessage(eq("阿里云短信测试"), eq("SMS_154950909"), eq("13800000000"), anyString())).thenReturn(true);

        Response<?> response = service.send(REQUEST, "1.2.3.4");

        org.junit.jupiter.api.Assertions.assertNotNull(response);
        verify(aliyunSmsHelper, times(1)).sendMessage(anyString(), anyString(), anyString(), anyString());
    }
}
