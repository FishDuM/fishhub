package hk.ljx.fishhub.user.dto.rsp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ResolveLoginableUserRspDTO {

    private Long userId;

    /**
     * 手机号对应的账号是否允许登录。
     */
    private boolean loginable;
}
