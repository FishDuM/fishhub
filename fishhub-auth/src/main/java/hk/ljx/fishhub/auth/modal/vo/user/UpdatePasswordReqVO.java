package hk.ljx.fishhub.auth.modal.vo.user;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新密码请求VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdatePasswordReqVO {

    @NotBlank(message = "新密码不能为空")
    @Max(value = 20, message = "新密码长度不能超过20个字符")
    @Min(value = 6, message = "新密码长度不能小于6个字符")
    private String newPassword;

}