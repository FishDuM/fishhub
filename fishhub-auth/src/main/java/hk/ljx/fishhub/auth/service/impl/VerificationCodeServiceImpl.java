package hk.ljx.fishhub.auth.service.impl;

import cn.hutool.core.util.RandomUtil;
import hk.ljx.framework.common.exception.BizException;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.auth.constant.RedisKeyConstants;
import hk.ljx.fishhub.auth.enums.ResponseCodeEnum;
import hk.ljx.fishhub.auth.model.vo.verificationcode.SendVerificationCodeReqVO;
import hk.ljx.fishhub.auth.service.VerificationCodeService;
import hk.ljx.fishhub.auth.sms.AliyunSmsHelper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;


@Service
@Slf4j
public class VerificationCodeServiceImpl implements VerificationCodeService {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private AliyunSmsHelper aliyunSmsHelper;

    private static final String GLOBAL_RATE_LIMIT_KEY = "verification_code:global:minute";
    private static final int GLOBAL_RATE_LIMIT_PER_MINUTE = 100;

    /**
     * 发送短信验证码
     *
     * @param sendVerificationCodeReqVO
     * @return
     */
    @Override
    public Response<?> send(SendVerificationCodeReqVO sendVerificationCodeReqVO) {
        // 手机号
        String phone = sendVerificationCodeReqVO.getPhone();

        // 构建验证码 redis key
        String key = RedisKeyConstants.buildVerificationCodeKey(phone);

        org.springframework.data.redis.core.script.DefaultRedisScript<Long> limitScript = new org.springframework.data.redis.core.script.DefaultRedisScript<>();
        limitScript.setScriptText("local current = redis.call('incr', KEYS[1]); " +
                "if current == 1 then redis.call('expire', KEYS[1], ARGV[1]); end; " +
                "return current;");
        limitScript.setResultType(Long.class);
        Long requestCount = (Long) redisTemplate.execute(limitScript, java.util.Collections.singletonList(GLOBAL_RATE_LIMIT_KEY), 60);
        if (requestCount != null && requestCount > GLOBAL_RATE_LIMIT_PER_MINUTE) {
            throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_SEND_FREQUENTLY);
        }

        // 生成 6 位随机数字验证码
        String verificationCode = RandomUtil.randomNumbers(6);

        Boolean reserved = redisTemplate.opsForValue()
                .setIfAbsent(key, verificationCode, 3, TimeUnit.MINUTES);
        if (!Boolean.TRUE.equals(reserved)) {
            throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_SEND_FREQUENTLY);
        }

        String signName = "阿里云短信测试";
        String templateCode = "SMS_154950909";
        String templateParam = String.format("{\"code\":\"%s\"}", verificationCode);
        boolean sent = aliyunSmsHelper.sendMessage(signName, templateCode, phone, templateParam);
        if (!sent) {
            redisTemplate.delete(key);
            throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_SEND_FAIL);
        }

        log.info("==> 验证码短信发送成功, phone: {}****{}",
                phone.substring(0, 3), phone.substring(phone.length() - 4));

        return Response.success();
    }
}
