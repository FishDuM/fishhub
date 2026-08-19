package hk.ljx.fishhub.note.biz.rpc;

import cn.hutool.core.collection.CollUtil;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.user.api.UserFeignApi;
import hk.ljx.fishhub.user.dto.req.FindUserByIdReqDTO;
import hk.ljx.fishhub.user.dto.req.FindUsersByIdsReqDTO;
import hk.ljx.fishhub.user.dto.resp.FindUserByIdRspDTO;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;


@Component
public class UserRpcService {

    @Resource
    private UserFeignApi userFeignApi;

    /**
     * 用户资料短缓存（容量 5000，过期 60s），消除热门创作者/大 V 资料在发现页列表和详情中的重复网络 Feign 开销
     */
    private static final Cache<Long, FindUserByIdRspDTO> USER_RPC_LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(1000)
            .maximumSize(5000)
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .build();

    /**
     * 查询用户信息（优先走本地短缓存）
     * @param userId
     * @return
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
     * 批量查询用户信息（优先走本地短缓存，仅对未命中用户发起增量 RPC）
     */
    public List<FindUserByIdRspDTO> findByIds(List<Long> userIds) {
        if (CollUtil.isEmpty(userIds)) {
            return List.of();
        }

        Map<Long, FindUserByIdRspDTO> hitMap = new HashMap<>(USER_RPC_LOCAL_CACHE.getAllPresent(userIds));
        List<Long> missedUserIds = userIds.stream()
                .filter(id -> id != null && !hitMap.containsKey(id))
                .distinct()
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
                .map(hitMap::get)
                .filter(Objects::nonNull)
                .toList();
    }



}
