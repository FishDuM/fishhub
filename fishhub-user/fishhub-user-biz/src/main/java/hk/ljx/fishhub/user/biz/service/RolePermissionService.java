package hk.ljx.fishhub.user.biz.service;

import cn.hutool.core.collection.CollUtil;
import hk.ljx.fishhub.user.biz.domain.dataobject.PermissionDO;
import hk.ljx.fishhub.user.biz.domain.dataobject.RoleDO;
import hk.ljx.fishhub.user.biz.domain.dataobject.RolePermissionDO;
import hk.ljx.fishhub.user.biz.domain.dataobject.UserRoleDO;
import hk.ljx.fishhub.user.biz.domain.mapper.PermissionDOMapper;
import hk.ljx.fishhub.user.biz.domain.mapper.RoleDOMapper;
import hk.ljx.fishhub.user.biz.domain.mapper.RolePermissionDOMapper;
import hk.ljx.fishhub.user.biz.domain.mapper.UserRoleDOMapper;
import hk.ljx.fishhub.user.dto.resp.UserRolePermissionRspDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 登录时的角色权限装配：查询用户角色与角色权限，
 * 供 auth 登录后写入 sa-token 会话（网关鉴权从会话读取，无独立缓存层）。
 */
@Service
@Slf4j
public class RolePermissionService {

    @Resource
    private UserRoleDOMapper userRoleDOMapper;
    @Resource
    private RoleDOMapper roleDOMapper;
    @Resource
    private RolePermissionDOMapper rolePermissionDOMapper;
    @Resource
    private PermissionDOMapper permissionDOMapper;

    public UserRolePermissionRspDTO findByUserId(Long userId) {
        List<UserRoleDO> userRoles = userRoleDOMapper.selectEnabledByUserId(userId);

        List<String> roles = new ArrayList<>();
        List<Long> roleIds = new ArrayList<>();
        for (UserRoleDO userRole : userRoles) {
            RoleDO roleDO = roleDOMapper.selectByPrimaryKey(userRole.getRoleId());
            // 只纳入未删除且启用（status=0）的角色，与启动时 selectEnabledList 口径一致
            if (roleDO == null || Boolean.TRUE.equals(roleDO.getIsDeleted())
                    || roleDO.getStatus() == null || roleDO.getStatus() != 0) {
                continue;
            }
            roles.add(roleDO.getRoleKey());
            roleIds.add(roleDO.getId());
        }

        Set<String> permissionKeys = new LinkedHashSet<>();
        if (CollUtil.isNotEmpty(roleIds)) {
            Map<Long, List<Long>> roleIdPermissionIdsMap = rolePermissionDOMapper.selectByRoleIds(roleIds).stream()
                    .collect(Collectors.groupingBy(RolePermissionDO::getRoleId,
                            Collectors.mapping(RolePermissionDO::getPermissionId, Collectors.toList())));
            Map<Long, String> permissionIdKeyMap = permissionDOMapper.selectAppEnabledList().stream()
                    .collect(Collectors.toMap(PermissionDO::getId, PermissionDO::getPermissionKey));

            roleIds.forEach(roleId -> {
                List<Long> permissionIds = roleIdPermissionIdsMap.get(roleId);
                if (CollUtil.isEmpty(permissionIds)) {
                    return;
                }
                permissionIds.forEach(permissionId -> {
                    String key = permissionIdKeyMap.get(permissionId);
                    if (key != null) {
                        permissionKeys.add(key);
                    }
                });
            });
        }

        return UserRolePermissionRspDTO.builder()
                .userId(userId)
                .roles(roles)
                .permissions(new ArrayList<>(permissionKeys))
                .build();
    }
}
