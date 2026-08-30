package hk.ljx.fishhub.user.biz.service;

import hk.ljx.fishhub.user.biz.domain.dataobject.UserDO;
import hk.ljx.fishhub.user.biz.domain.mapper.UserDOMapper;
import hk.ljx.fishhub.user.dto.rsp.UserRolePermissionRspDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 登录时的角色权限装配：
 * 默认普通用户拥有发笔记、发评论权限；若用户被禁用或删除则无权限。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RolePermissionService {

    public static final List<String> DEFAULT_ROLES = List.of("common_user");
    public static final List<String> DEFAULT_PERMISSIONS = List.of("app:note:publish", "app:comment:publish");

    private final UserDOMapper userDOMapper;

    public UserRolePermissionRspDTO findByUserId(Long userId) {
        if (userId == null) {
            return empty(userId);
        }

        UserDO userDO = userDOMapper.selectByPrimaryKey(userId);
        if (userDO == null || Boolean.TRUE.equals(userDO.getIsDeleted())
                || userDO.getStatus() == null || userDO.getStatus() != 0) {
            return empty(userId);
        }

        return UserRolePermissionRspDTO.builder()
                .userId(userId)
                .roles(DEFAULT_ROLES)
                .permissions(DEFAULT_PERMISSIONS)
                .build();
    }

    /** 角色/权限变更后失效缓存（保留方法签名兼容性） */
    public void evict(Long userId) {
        // 无表化后内存常量直出，无需淘汰复杂缓存
    }

    private static UserRolePermissionRspDTO empty(Long userId) {
        return UserRolePermissionRspDTO.builder()
                .userId(userId)
                .roles(List.of())
                .permissions(List.of())
                .build();
    }
}

