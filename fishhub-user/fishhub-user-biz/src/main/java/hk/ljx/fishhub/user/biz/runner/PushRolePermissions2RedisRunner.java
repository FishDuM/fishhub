package hk.ljx.fishhub.user.biz.runner;

import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.user.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.user.biz.domain.dataobject.PermissionDO;
import hk.ljx.fishhub.user.biz.domain.dataobject.RoleDO;
import hk.ljx.fishhub.user.biz.domain.dataobject.RolePermissionDO;
import hk.ljx.fishhub.user.biz.domain.dataobject.UserRoleDO;
import hk.ljx.fishhub.user.biz.domain.mapper.PermissionDOMapper;
import hk.ljx.fishhub.user.biz.domain.mapper.RoleDOMapper;
import hk.ljx.fishhub.user.biz.domain.mapper.RolePermissionDOMapper;
import hk.ljx.fishhub.user.biz.domain.mapper.UserRoleDOMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static hk.ljx.fishhub.user.biz.constant.RoleConstants.COMMON_USER_ROLE_ID;


@Component
@Slf4j
public class PushRolePermissions2RedisRunner implements ApplicationRunner {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RoleDOMapper roleDOMapper;
    @Resource
    private PermissionDOMapper permissionDOMapper;
    @Resource
    private RolePermissionDOMapper rolePermissionDOMapper;
    @Resource
    private UserRoleDOMapper userRoleDOMapper;

    @Override
    public void run(ApplicationArguments args) {
        log.info("==> 服务启动，开始同步角色权限数据到 Redis 中...");

        try {
            // 查询出所有角色
            List<RoleDO> roleDOS = roleDOMapper.selectEnabledList();

            if (CollUtil.isNotEmpty(roleDOS)) {
                // 拿到所有角色的 ID
                List<Long> roleIds = roleDOS.stream().map(RoleDO::getId).toList();

                // 根据角色 ID, 批量查询出所有角色对应的权限
                List<RolePermissionDO> rolePermissionDOS = rolePermissionDOMapper.selectByRoleIds(roleIds);
                // 按角色 ID 分组, 每个角色 ID 对应多个权限 ID
                Map<Long, List<Long>> roleIdPermissionIdsMap = rolePermissionDOS.stream().collect(
                        Collectors.groupingBy(RolePermissionDO::getRoleId,
                                Collectors.mapping(RolePermissionDO::getPermissionId, Collectors.toList()))
                );

                // 查询 APP 端所有被启用的权限
                List<PermissionDO> permissionDOS = permissionDOMapper.selectAppEnabledList();
                // 权限 ID - 权限 DO
                Map<Long, PermissionDO> permissionIdDOMap = permissionDOS.stream().collect(
                        Collectors.toMap(PermissionDO::getId, permissionDO -> permissionDO)
                );

                // 组织 角色-权限 关系
                Map<String, List<String>> roleKeyPermissionsMap = Maps.newHashMap();

                // 循环所有角色
                roleDOS.forEach(roleDO -> {
                    // 当前角色 ID
                    Long roleId = roleDO.getId();
                    // 当前角色 roleKey
                    String roleKey = roleDO.getRoleKey();
                    // 当前角色 ID 对应的权限 ID 集合
                    List<Long> permissionIds = roleIdPermissionIdsMap.get(roleId);
                    if (CollUtil.isNotEmpty(permissionIds)) {
                        List<String> permissionKeys = Lists.newArrayList();
                        permissionIds.forEach(permissionId -> {
                            // 根据权限 ID 获取具体的权限 DO 对象
                            PermissionDO permissionDO = permissionIdDOMap.get(permissionId);
                            if (Objects.nonNull(permissionDO)) {
                                permissionKeys.add(permissionDO.getPermissionKey());
                            }
                        });
                        roleKeyPermissionsMap.put(roleKey, permissionKeys);
                    }
                });

                // 同步至 Redis 中，方便后续网关查询 Redis, 用于鉴权
                roleKeyPermissionsMap.forEach((roleKey, permissions) -> {
                    String key = RedisKeyConstants.buildRolePermissionsKey(roleKey);
                    stringRedisTemplate.opsForValue().set(key, JsonUtils.toJsonString(permissions));
                });

                // 历史导入用户也必须具备系统默认角色，不能只在注册流程中赋权。
                userRoleDOMapper.insertDefaultRoleForUsersWithoutRole(COMMON_USER_ROLE_ID);

                Map<Long, String> roleIdAndKeyMap = roleDOS.stream()
                        .collect(Collectors.toMap(RoleDO::getId, RoleDO::getRoleKey));
                Map<Long, List<String>> userRolesMap = userRoleDOMapper.selectEnabledList().stream()
                        .filter(userRoleDO -> roleIdAndKeyMap.containsKey(userRoleDO.getRoleId()))
                        .collect(Collectors.groupingBy(
                                UserRoleDO::getUserId,
                                Collectors.mapping(userRoleDO -> roleIdAndKeyMap.get(userRoleDO.getRoleId()),
                                        Collectors.toList())));

                userRolesMap.forEach((userId, roles) -> stringRedisTemplate.opsForValue().set(
                        RedisKeyConstants.buildUserRoleKey(userId), JsonUtils.toJsonString(roles)));
            }

            log.info("==> 服务启动，成功同步角色权限数据到 Redis 中...");
        } catch (Exception e) {
            log.error("==> 同步角色权限数据到 Redis 中失败: ", e);
        }

    }
}
