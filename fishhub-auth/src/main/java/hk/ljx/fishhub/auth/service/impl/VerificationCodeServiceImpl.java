package hk.ljx.fishhub.auth.service.impl;

import cn.hutool.core.util.RandomUtil;
import hk.ljx.fishhub.auth.constant.RedisKeyConstants;
import hk.ljx.fishhub.auth.enums.ResponseCodeEnum;
import hk.ljx.fishhub.auth.modal.vo.verificationcode.SendVerificationCodeReqVO;
import hk.ljx.fishhub.auth.service.VerificationCodeService;
import hk.ljx.fishhub.auth.sms.AliyunSmsHelper;
import hk.ljx.framework.common.exception.BizException;
import hk.ljx.framework.common.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class VerificationCodeServiceImpl implements VerificationCodeService {

    @Resource
    private RedisTemplate<String,Object> redisTemplate;

    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    @Resource
    private AliyunSmsHelper aliyunSmsHelper;

    @Override
    public Response<?> send(SendVerificationCodeReqVO sendVerificationCodeReqVO) {
        String phone = sendVerificationCodeReqVO.getPhone();
        String redisSendVerificationCodeKey = RedisKeyConstants.buildVerificationCodeKey(phone);
        Boolean isSent = redisTemplate.hasKey(redisSendVerificationCodeKey);
        if (isSent) {
            // 发送频繁
            throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_SEND_FREQUENTLY);
        }
        // 生成验证码
        String verificationCode = RandomUtil.randomNumbers(6);
        log.info("发送短信验证码，手机号：{}，验证码：{}", phone, verificationCode);

        // 调用第三方短信服务
        threadPoolTaskExecutor.submit(() -> {
            String signName = "速通互联验证码";
            String templateCode = "100001";
            // 短信模板参数，code 表示要发送的验证码；min 表示验证码有时间时长，即 3 分钟
            String templateParam = String.format("{\"code\":\"%s\",\"min\":\"3\"}", verificationCode);
            aliyunSmsHelper.sendMessage(signName, templateCode, phone, templateParam);
        });

        // 存入redis, cd 3分钟
        redisTemplate.opsForValue().set(redisSendVerificationCodeKey, verificationCode, 3, TimeUnit.MINUTES);
        return Response.success();
    }
}
