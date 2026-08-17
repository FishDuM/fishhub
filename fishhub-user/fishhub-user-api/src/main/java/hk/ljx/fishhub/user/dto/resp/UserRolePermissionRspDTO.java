package hk.ljx.fishhub.user.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRolePermissionRspDTO {

    private Long userId;

    /**
     * 用户拥有的角色 roleKey 列表
     */
    private List<String> roles;

    /**
     * 各角色权限去重合并后的 permissionKey 列表
     */
    private List<String> permissions;
}
