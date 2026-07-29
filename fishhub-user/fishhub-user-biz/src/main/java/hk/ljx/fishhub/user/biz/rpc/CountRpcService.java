package hk.ljx.fishhub.user.biz.rpc;

import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.count.api.CountFeignApi;
import hk.ljx.fishhub.count.dto.FindNoteCountByIdReqDTO;
import hk.ljx.fishhub.count.dto.FindNoteCountByIdRspDTO;
import hk.ljx.fishhub.count.dto.FindUserCountByIdReqDTO;
import hk.ljx.fishhub.count.dto.FindUserCountByIdRspDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class CountRpcService {

    @Resource
    private CountFeignApi countFeignApi;

    /**
     * 查询用户计数信息
     * @param userId
     * @return
     */
    public FindUserCountByIdRspDTO findUserCountById(Long userId) {
        FindUserCountByIdReqDTO findUserCountByIdReqDTO = new FindUserCountByIdReqDTO();
        findUserCountByIdReqDTO.setUserId(userId);

        Response<FindUserCountByIdRspDTO> response = countFeignApi.findUserCount(findUserCountByIdReqDTO);

        if (Objects.isNull(response) || !response.isSuccess()) {
            return null;
        }

        return response.getData();
    }

}

