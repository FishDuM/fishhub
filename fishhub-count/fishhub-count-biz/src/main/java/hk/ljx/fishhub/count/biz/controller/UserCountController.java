package hk.ljx.fishhub.count.biz.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import hk.ljx.framework.biz.operationlog.aspect.ApiOperationLog;
import hk.ljx.framework.common.response.Response;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.count.biz.service.UserCountService;
import hk.ljx.fishhub.count.dto.FindUserCountsByIdReqDTO;
import hk.ljx.fishhub.count.dto.FindUserCountsByIdRspDTO;
import hk.ljx.fishhub.count.dto.FindUserCountsByIdsReqDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;



@RestController
@RequestMapping("/count")
@Slf4j
@RequiredArgsConstructor
public class UserCountController {

    private final UserCountService userCountService;

    @PostMapping(value = "/user/data")
    @ApiOperationLog(description = "获取用户计数数据")
    @SentinelResource(value = "findUserCountData4Controller", blockHandler = "blockHandler4findUserCountData")
    public Response<FindUserCountsByIdRspDTO> findUserCountData(@Validated @RequestBody FindUserCountsByIdReqDTO findUserCountsByIdReqDTO) {
        return userCountService.findUserCountData(findUserCountsByIdReqDTO);
    }

    @PostMapping(value = "/users/data")
    @ApiOperationLog(description = "批量获取用户计数数据")
    public Response<List<FindUserCountsByIdRspDTO>> findUsersCountData(
            @Validated @RequestBody FindUserCountsByIdsReqDTO findUserCountsByIdsReqDTO) {
        return userCountService.findUsersCountData(findUserCountsByIdsReqDTO);
    }

    /**
     * blockHandler 函数，原方法调用被限流/降级/系统保护的时候调用
     * 注意, 需要包含限流方法的所有参数，和 BlockException 参数
     * @param findUserCountsByIdReqDTO
     * @param blockException
     */
    public Response<FindUserCountsByIdRspDTO> blockHandler4findUserCountData(FindUserCountsByIdReqDTO findUserCountsByIdReqDTO, BlockException blockException) {
        log.warn("## /count/user/count 接口被限流: {}", JsonUtils.toJsonString(findUserCountsByIdReqDTO));

        return Response.success(FindUserCountsByIdRspDTO.builder()
                .userId(findUserCountsByIdReqDTO.getUserId())
                .collectTotal(0L)
                .fansTotal(0L)
                .followingTotal(0L)
                .likeTotal(0L)
                .noteTotal(0L)
                .build());
    }

}
