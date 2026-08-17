package hk.ljx.fishhub.user.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FindUserRolePermissionReqDTO {

    @NotNull(message = "用户ID不能为空")
    private Long userId;
}
