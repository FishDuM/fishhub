package hk.ljx.fishhub.search.api;

import hk.ljx.fishhub.search.dto.req.RebuildNoteDocumentReqDTO;
import hk.ljx.fishhub.search.dto.req.RebuildUserDocumentReqDTO;
import hk.ljx.framework.common.response.Response;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import static hk.ljx.fishhub.search.constant.ApiConstants.SERVICE_NAME;

/** 提供给其他服务调用的搜索服务接口。 */
@FeignClient(name = SERVICE_NAME)
public interface SearchFeignApi {

    String PREFIX = "/search";

    @PostMapping(PREFIX + "/note/document/rebuild")
    Response<Long> rebuildNoteDocument(@RequestBody RebuildNoteDocumentReqDTO request);

    @PostMapping(PREFIX + "/user/document/rebuild")
    Response<Long> rebuildUserDocument(@RequestBody RebuildUserDocumentReqDTO request);
}
