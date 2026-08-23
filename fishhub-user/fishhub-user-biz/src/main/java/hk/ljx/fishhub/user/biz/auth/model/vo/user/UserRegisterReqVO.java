package hk.ljx.fishhub.user.biz.auth.model.vo.user;

import hk.ljx.framework.common.validator.PhoneNumber;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserRegisterReqVO {

    /**
     * 手机号
     */
    @NotBlank(message = "手机号不能为空")
    @PhoneNumber
    private String phone;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    @Length(min = 6, max = 20, message = "密码长度需在 6-20 位之间")
    private String password;

    /**
     * 图形验证码 Key
     */
    @NotBlank(message = "验证码标识不能为空")
    private String captchaKey;

    /**
     * 图形验证码
     */
    @NotBlank(message = "验证码不能为空")
    private String captchaCode;
}
