package hk.ljx.fishhub.note.api;

import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.note.constant.ApiConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = ApiConstants.SERVICE_NAME)
public interface NoteFeignApi {

    @PostMapping("/note/exists")
    Response<Boolean> exists(@RequestBody Long noteId);

    @PostMapping("/note/accessible")
    Response<Boolean> isAccessible(@RequestBody Long noteId);

    @PostMapping("/note/accessible/batch")
    Response<List<Long>> findAccessibleNoteIds(@RequestBody List<Long> noteIds);

    /**
     * 供异步写入消费者批量校验笔记当前是否允许指定用户写入；该接口始终查询 MySQL。
     */
    @PostMapping("/note/writable/batch")
    Response<List<NoteWriteAccessCheckReqDTO>> findWritableNoteAccesses(
            @RequestBody List<NoteWriteAccessCheckReqDTO> requests);
}
