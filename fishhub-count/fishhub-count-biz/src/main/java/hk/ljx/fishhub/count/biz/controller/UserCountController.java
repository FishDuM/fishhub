package hk.ljx.fishhub.count.biz.controller;

import hk.ljx.fishhub.framework.biz.operationlog.aspect.ApiOperationLog;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.count.biz.service.NoteCountService;
import hk.ljx.fishhub.count.biz.service.UserCountService;
import hk.ljx.fishhub.count.dto.FindNoteCountByIdReqDTO;
import hk.ljx.fishhub.count.dto.FindNoteCountByIdRspDTO;
import hk.ljx.fishhub.count.dto.FindUserCountByIdReqDTO;
import hk.ljx.fishhub.count.dto.FindUserCountByIdRspDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/count")
@Slf4j
public class UserCountController {

    @Resource
    private UserCountService userCountService;

    @PostMapping(value = "/user/data")
    @ApiOperationLog(description = "获取用户计数数据")
    public Response<FindUserCountByIdRspDTO> findUserCountData(@Validated @RequestBody FindUserCountByIdReqDTO findUserCountByIdReqDTO) {
        return userCountService.findUserCountData(findUserCountByIdReqDTO);
    }

}

