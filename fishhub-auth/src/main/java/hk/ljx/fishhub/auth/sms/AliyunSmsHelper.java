package hk.ljx.fishhub.auth.sms;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teautil.models.RuntimeOptions;
import hk.ljx.framework.common.util.JsonUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


@Component
@Slf4j
public class AliyunSmsHelper {

    @Resource
    private Client client;

    /**
     * 发送短信
     * @param signName
     * @param templateCode
     * @param phone
     * @param templateParam
     * @return
     */
    public boolean sendMessage(String signName, String templateCode, String phone, String templateParam) {
        SendSmsRequest sendSmsRequest = new SendSmsRequest()
                .setSignName(signName)
                .setTemplateCode(templateCode)
                .setPhoneNumbers(phone)
                .setTemplateParam(templateParam);
        RuntimeOptions runtime = new RuntimeOptions();

        try {
            log.info("==> 开始短信发送, phoneSuffix: {}, signName: {}, templateCode: {}",
                    phone.substring(Math.max(0, phone.length() - 4)), signName, templateCode);

            // 发送短信
            SendSmsResponse response = client.sendSmsWithOptions(sendSmsRequest, runtime);

            boolean success = response != null && response.getBody() != null
                    && "OK".equalsIgnoreCase(response.getBody().getCode());
            if (success) {
                log.info("==> 短信发送成功, response: {}", JsonUtils.toJsonString(response));
            } else {
                log.warn("==> 短信平台返回失败, code: {}, message: {}",
                        response == null || response.getBody() == null ? null : response.getBody().getCode(),
                        response == null || response.getBody() == null ? null : response.getBody().getMessage());
            }
            return success;
        } catch (Exception error) {
            log.error("==> 短信发送错误: ", error);
            return false;
        }
    }
}
