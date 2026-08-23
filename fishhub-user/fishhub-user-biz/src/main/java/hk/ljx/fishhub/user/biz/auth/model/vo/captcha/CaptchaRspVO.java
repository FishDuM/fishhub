package hk.ljx.fishhub.user.biz.auth.model.vo.captcha;
 
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
 
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CaptchaRspVO {
 
    /**
     * 验证码唯一标识
     */
    private String captchaKey;
 
    /**
     * 验证码 Base64 图片 (包含 data:image/png;base64, 前缀)
     */
    private String captchaBase64;
}
