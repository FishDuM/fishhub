package hk.ljx.fishhub.comment.biz.rpc;

import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.note.api.NoteFeignApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class NoteRpcService {

    @Resource
    private NoteFeignApi noteFeignApi;

    public boolean exists(Long noteId) {
        Response<Boolean> response = noteFeignApi.exists(noteId);
        return response != null && response.isSuccess() && Boolean.TRUE.equals(response.getData());
    }
}
