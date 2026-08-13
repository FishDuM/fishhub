package hk.ljx.fishhub.note.api;

import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.note.constant.ApiConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = ApiConstants.SERVICE_NAME)
public interface NoteFeignApi {

    @PostMapping("/note/exists")
    Response<Boolean> exists(@RequestBody Long noteId);
}
