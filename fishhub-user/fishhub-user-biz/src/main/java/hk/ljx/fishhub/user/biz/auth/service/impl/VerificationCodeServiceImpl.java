package hk.ljx.fishhub.user.biz.auth.service.impl;

import cn.hutool.core.util.RandomUtil;
import hk.ljx.framework.common.exception.BizException;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.user.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.user.biz.auth.enums.ResponseCodeEnum;
import hk.ljx.fishhub.user.biz.auth.model.vo.verificationcode.SendVerificationCodeReqVO;
import hk.ljx.fishhub.user.biz.auth.service.VerificationCodeService;
import hk.ljx.fishhub.user.biz.auth.sms.AliyunSmsHelper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;


@Service
@Slf4j
public class VerificationCodeServiceImpl implements VerificationCodeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private AliyunSmsHelper aliyunSmsHelper;

    private static final int PHONE_RATE_LIMIT_PER_MINUTE = 100;
    private static final int IP_RATE_LIMIT_PER_MINUTE = 500;
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('incr', KEYS[1]); "
                    + "if current == 1 then redis.call('expire', KEYS[1], ARGV[1]); end; "
                    + "return current;",
            Long.class);

    /**
     * 发送短信验证码
     *
     * @param sendVerificationCodeReqVO
     * @return
     */
    @Override
    public Response<?> send(SendVerificationCodeReqVO sendVerificationCodeReqVO, String clientIp) {
        // 手机号
        String phone = sendVerificationCodeReqVO.getPhone();

        // 构建验证码 redis key
        String key = RedisKeyConstants.buildVerificationCodeKey(phone);

        // 双维度分钟限流：手机号/IP 任一超限即拒绝
        Long phoneCount = stringRedisTemplate.execute(RATE_LIMIT_SCRIPT,
                java.util.Collections.singletonList(RedisKeyConstants.buildPhoneRateLimitKey(phone)), String.valueOf(60));
        if (phoneCount != null && phoneCount > PHONE_RATE_LIMIT_PER_MINUTE) {
            throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_SEND_FREQUENTLY);
        }
        if (StringUtils.isNotBlank(clientIp)) {
            Long ipCount = stringRedisTemplate.execute(RATE_LIMIT_SCRIPT,
                    java.util.Collections.singletonList(RedisKeyConstants.buildIpRateLimitKey(clientIp)), String.valueOf(60));
            if (ipCount != null && ipCount > IP_RATE_LIMIT_PER_MINUTE) {
                throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_SEND_FREQUENTLY);
            }
        }

        // 生成 6 位随机数字验证码
        String verificationCode = RandomUtil.randomNumbers(6);

        Boolean reserved = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, verificationCode, 3, TimeUnit.MINUTES);
        if (!Boolean.TRUE.equals(reserved)) {
            throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_SEND_FREQUENTLY);
        }

        String signName = "阿里云短信测试";
        String templateCode = "SMS_154950909";
        String templateParam = String.format("{\"code\":\"%s\"}", verificationCode);
        boolean sent = aliyunSmsHelper.sendMessage(signName, templateCode, phone, templateParam);
        if (!sent) {
            stringRedisTemplate.delete(key);
            throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_SEND_FAIL);
        }

        log.info("==> 验证码短信发送成功, phone: {}****{}",
                phone.substring(0, 3), phone.substring(phone.length() - 4));

        return Response.success();
    }
}
