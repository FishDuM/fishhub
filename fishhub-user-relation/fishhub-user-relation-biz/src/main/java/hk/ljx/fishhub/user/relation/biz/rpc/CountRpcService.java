package hk.ljx.fishhub.user.relation.biz.rpc;

import cn.hutool.core.collection.CollUtil;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.count.api.CountFeignApi;
import hk.ljx.fishhub.count.dto.FindUserCountsByIdRspDTO;
import hk.ljx.fishhub.count.dto.FindUserCountsByIdsReqDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Component
public class CountRpcService {

    @Resource
    private CountFeignApi countFeignApi;

    public List<FindUserCountsByIdRspDTO> findByUserIds(List<Long> userIds) {
        if (CollUtil.isEmpty(userIds)) {
            return Collections.emptyList();
        }

        Response<List<FindUserCountsByIdRspDTO>> response = countFeignApi.findUsersCount(
                FindUserCountsByIdsReqDTO.builder().userIds(userIds).build());
        if (Objects.isNull(response) || !response.isSuccess() || CollUtil.isEmpty(response.getData())) {
            return Collections.emptyList();
        }
        return response.getData();
    }
}
