package hk.ljx.fishhub.auth.runner;

import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import hk.ljx.fishhub.auth.constant.RedisKeyConstants;
import hk.ljx.fishhub.auth.domain.dataobject.PermissionDO;
import hk.ljx.fishhub.auth.domain.dataobject.RoleDO;
import hk.ljx.fishhub.auth.domain.dataobject.RolePermissionDO;
import hk.ljx.fishhub.auth.domain.mapper.PermissionDOMapper;
import hk.ljx.fishhub.auth.domain.mapper.RoleDOMapper;
import hk.ljx.fishhub.auth.domain.mapper.RolePermissionDOMapper;
import hk.ljx.framework.common.util.JsonUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 服务启动时，将角色权限数据同步到 Redis 中
 */
@Component
@Slf4j
public class PushRolePermissions2RedisRunner implements ApplicationRunner {

    @Resource
    private RoleDOMapper roleDOMapper;

    @Resource
    private RolePermissionDOMapper rolePermissionDOMapper;

    @Resource
    private PermissionDOMapper permissionDOMapper;

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    // 权限同步标记 Key
    private static final String PUSH_PERMISSION_FLAG = "push.permission.flag";

    @Override
    public void run(ApplicationArguments args) {
        log.info("服务启动，开始同步角色权限数据到 Redis 中...");
        try {
            // 是否能够同步数据: 原子操作，只有在键 PUSH_PERMISSION_FLAG 不存在时，才会设置该键的值为 "1"，并设置过期时间为 1 天
            boolean canPushed = redisTemplate.opsForValue().setIfAbsent(PUSH_PERMISSION_FLAG, "1", 1, TimeUnit.DAYS);

            // 如果无法同步权限数据
            if (!canPushed) {
                log.warn("==> 角色权限数据已经同步至 Redis 中，不再同步...");
                return;
            }
            // 查询出所有角色
            List<RoleDO> roleDOS = roleDOMapper.selectEnabledList();
            if (CollUtil.isNotEmpty(roleDOS)) {
                // 获得所有角色的 id
                List<Long> roleIds = roleDOS.stream().map(RoleDO::getId).toList();
                // 根据角色 id 查询出所有角色权限
                List<RolePermissionDO> rolePermissionDOS = rolePermissionDOMapper.selectByRoleIds(roleIds);
                // 按角色 id 分组，每个角色 id 对应多个权限 id
                Map<Long, List<Long>> roleIdPermissionIdsMap = rolePermissionDOS.stream().collect(
                        Collectors.groupingBy(RolePermissionDO::getRoleId,
                                Collectors.mapping(RolePermissionDO::getPermissionId, Collectors.toList())));

                // 查询 app 端所有被启用的权限
                List<PermissionDO> permissionDOS = permissionDOMapper.selectAppEnabledList();
                // 权限 ID - 权限 DO
                Map<Long, PermissionDO> permissionIdDOMap = permissionDOS.stream().collect(
                        Collectors.toMap(PermissionDO::getId, permissionDO -> permissionDO));
                // 角色 id - 权限
                HashMap<String, List<String>> roleKeyPermissionsMap = Maps.newHashMap();

                //循环所有角色
                roleDOS.forEach(roleDO -> {
                    // 当前角色 id
                    Long roleId = roleDO.getId();
                    String roleKey = roleDO.getRoleKey();
                    // 当前角色对应的权限 id 集合
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

                // 同步至 Redis 中，方便后续网关查询鉴权使用
                roleKeyPermissionsMap.forEach((roleKey, permissions) -> {
                    String key = RedisKeyConstants.buildRolePermissionsKey(roleKey);
                    redisTemplate.opsForValue().set(key, JsonUtils.toJsonString(permissions));
                });
            }
            log.info("服务启动，成功同步角色权限数据到 Redis 中...");
        } catch (Exception e) {
            log.error("同步角色权限数据到 Redis 中失败: ", e);
        }
    }
}