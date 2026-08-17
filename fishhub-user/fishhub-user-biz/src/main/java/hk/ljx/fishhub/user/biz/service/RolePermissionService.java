package hk.ljx.fishhub.user.biz.service;

import cn.hutool.core.collection.CollUtil;
import hk.ljx.framework.common.util.CacheTtl;
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
import hk.ljx.fishhub.user.dto.resp.UserRolePermissionRspDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 登录时的角色权限装配：查询用户角色与角色权限，
 * 供 auth 登录后写入 sa-token 会话（网关鉴权从会话读取）。
 * <p>角色-权限低频变更，加 Redis 快照缓存（30min±抖动 TTL），变更时 {@link #evict(Long)} 失效。
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
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public UserRolePermissionRspDTO findByUserId(Long userId) {
        String cacheKey = RedisKeyConstants.buildUserRolePermissionKey(userId);
        UserRolePermissionRspDTO cached = readFromCache(cacheKey);
        if (cached != null) {
            return cached;
        }
        UserRolePermissionRspDTO result = loadFromDb(userId);
        writeToCache(cacheKey, result);
        return result;
    }

    /** 角色/权限变更后失效缓存（下次登录重新装配）。 */
    public void evict(Long userId) {
        try {
            stringRedisTemplate.delete(RedisKeyConstants.buildUserRolePermissionKey(userId));
        } catch (Exception e) {
            log.warn("Redis 不可用，角色权限缓存删除失败，等待 TTL 兜底, userId={}", userId, e);
        }
    }

    private UserRolePermissionRspDTO readFromCache(String cacheKey) {
        try {
            String json = stringRedisTemplate.opsForValue().get(cacheKey);
            if (json == null) {
                return null;
            }
            UserRolePermissionRspDTO dto = JsonUtils.parseObject(json, UserRolePermissionRspDTO.class);
            // 旧版本/损坏缓存兜底回源
            return dto != null && dto.getRoles() != null ? dto : null;
        } catch (Exception e) {
            log.warn("Redis 不可用，角色权限缓存读取失败，回源 MySQL, key={}", cacheKey, e);
            return null;
        }
    }

    private void writeToCache(String cacheKey, UserRolePermissionRspDTO dto) {
        try {
            long expireSeconds = CacheTtl.minutes(30, 5);
            stringRedisTemplate.opsForValue().set(cacheKey, JsonUtils.toJsonString(dto), expireSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis 不可用，角色权限缓存写入失败，响应将继续返回, key={}", cacheKey, e);
        }
    }

    private UserRolePermissionRspDTO loadFromDb(Long userId) {
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
