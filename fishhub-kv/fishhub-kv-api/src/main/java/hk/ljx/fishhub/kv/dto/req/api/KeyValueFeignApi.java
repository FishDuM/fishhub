package hk.ljx.fishhub.kv.dto.req.api;

import hk.ljx.fishhub.kv.dto.req.dto.req.AddNoteContentReqDTO;
import hk.ljx.fishhub.kv.dto.req.dto.req.DeleteNoteContentReqDTO;
import hk.ljx.fishhub.kv.dto.req.dto.req.FindNoteContentReqDTO;
import hk.ljx.fishhub.kv.dto.req.dto.rsp.FindNoteContentRspDTO;
import hk.ljx.framework.common.response.Response;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import static hk.ljx.fishhub.kv.dto.req.contant.ApiConstants.SERVICE_NAME;

@FeignClient(name = SERVICE_NAME)
public interface KeyValueFeignApi {

    String PREFIX = "/kv";

    @PostMapping(value = PREFIX + "/note/content/add")
    Response<?> addNoteContent(@RequestBody AddNoteContentReqDTO addNoteContentReqDTO);

    @PostMapping(value = PREFIX + "/note/content/find")
    Response<FindNoteContentRspDTO> findNoteContent(@RequestBody FindNoteContentReqDTO findNoteContentReqDTO);

    @PostMapping(value = PREFIX + "/note/content/delete")
    Response<?> deleteNoteContent (@RequestBody DeleteNoteContentReqDTO deleteNoteContentReqDTO);

}
