package hk.ljx.fishhub.user.client;

import cn.hutool.core.collection.CollUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.user.api.UserFeignApi;
import hk.ljx.fishhub.user.dto.req.FindUserByIdReqDTO;
import hk.ljx.fishhub.user.dto.req.FindUsersByIdsReqDTO;
import hk.ljx.fishhub.user.dto.rsp.FindUserByIdRspDTO;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 用户服务 RPC 客户端
 */
@RequiredArgsConstructor
public class UserClient {

    private final UserFeignApi userFeignApi;

    /**
     * 用户资料本地缓存（兜底 10 秒失效，优先由 RocketMQ 广播毫秒级主动失效）
     */
    private static final Cache<Long, FindUserByIdRspDTO> USER_RPC_LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(1000)
            .maximumSize(5000)
            .expireAfterWrite(10, TimeUnit.SECONDS)
            .build();

    /**
     * 主动失效指定用户的本地内存缓存（由 MQ 广播消费触发）
     */
    public static void invalidate(Long userId) {
        if (userId != null) {
            USER_RPC_LOCAL_CACHE.invalidate(userId);
        }
    }

    /**
     * 清空全部本地用户缓存
     */
    public static void invalidateAll() {
        USER_RPC_LOCAL_CACHE.invalidateAll();
    }

    /**
     * 查询用户信息
     */
    public FindUserByIdRspDTO findById(Long userId) {
        if (userId == null) {
            return null;
        }
        FindUserByIdRspDTO cached = USER_RPC_LOCAL_CACHE.getIfPresent(userId);
        if (cached != null) {
            return cached;
        }

        FindUserByIdReqDTO findUserByIdReqDTO = new FindUserByIdReqDTO();
        findUserByIdReqDTO.setId(userId);

        Response<FindUserByIdRspDTO> response = userFeignApi.findById(findUserByIdReqDTO);

        if (Objects.isNull(response) || !response.isSuccess() || response.getData() == null) {
            return null;
        }

        FindUserByIdRspDTO data = response.getData();
        USER_RPC_LOCAL_CACHE.put(userId, data);
        return data;
    }

    /**
     * 查询未禁用、未删除的用户。
     */
    public FindUserByIdRspDTO findActiveById(Long userId) {
        if (userId == null) {
            return null;
        }
        FindUserByIdReqDTO request = new FindUserByIdReqDTO();
        request.setId(userId);

        Response<FindUserByIdRspDTO> response = userFeignApi.findActiveById(request);
        if (Objects.isNull(response) || !response.isSuccess()) {
            throw new IllegalStateException("查询用户信息失败, userId=" + userId);
        }
        if (response.getData() == null) {
            return null;
        }
        return response.getData();
    }

    /**
     * 批量查询用户信息
     */
    public List<FindUserByIdRspDTO> findByIds(List<Long> userIds) {
        if (CollUtil.isEmpty(userIds)) {
            return List.of();
        }

        List<Long> nonNullIds = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (CollUtil.isEmpty(nonNullIds)) {
            return List.of();
        }

        Map<Long, FindUserByIdRspDTO> hitMap = new HashMap<>(USER_RPC_LOCAL_CACHE.getAllPresent(nonNullIds));
        List<Long> missedUserIds = nonNullIds.stream()
                .filter(id -> !hitMap.containsKey(id))
                .toList();

        if (CollUtil.isNotEmpty(missedUserIds)) {
            FindUsersByIdsReqDTO request = new FindUsersByIdsReqDTO();
            request.setIds(missedUserIds);
            Response<List<FindUserByIdRspDTO>> response = userFeignApi.findByIds(request);
            if (response != null && response.isSuccess() && CollUtil.isNotEmpty(response.getData())) {
                for (FindUserByIdRspDTO user : response.getData()) {
                    if (user != null && user.getId() != null) {
                        USER_RPC_LOCAL_CACHE.put(user.getId(), user);
                        hitMap.put(user.getId(), user);
                    }
                }
            }
        }

        return userIds.stream()
                .filter(Objects::nonNull)
                .map(hitMap::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
