package hk.ljx.fishhub.comment.biz.rpc;

import cn.hutool.core.collection.CollUtil;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.note.api.NoteFeignApi;
import hk.ljx.fishhub.note.api.NoteWriteAccessCheckReqDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class NoteRpcService {

    private final NoteFeignApi noteFeignApi;

    public boolean exists(Long noteId) {
        Response<Boolean> response = noteFeignApi.exists(noteId);
        return response != null && response.isSuccess() && Boolean.TRUE.equals(response.getData());
    }

    public boolean isAccessible(Long noteId) {
        Response<Boolean> response = noteFeignApi.isAccessible(noteId);
        if (response == null || !response.isSuccess()) {
            throw new IllegalStateException("笔记访问鉴权服务调用失败");
        }
        return Boolean.TRUE.equals(response.getData());
    }

    public List<Long> findAccessibleNoteIds(List<Long> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) {
            return Collections.emptyList();
        }
        Response<List<Long>> response = noteFeignApi.findAccessibleNoteIds(noteIds);
        if (response == null || !response.isSuccess()) {
            throw new IllegalStateException("笔记批量访问鉴权服务调用失败");
        }
        return response.getData() == null ? Collections.emptyList() : response.getData();
    }

    /**
     * 仅供 MQ 消费端调用，服务端直接以 MySQL 当前状态裁决写权限。
     */
    public List<NoteWriteAccessCheckReqDTO> findWritableNoteAccesses(List<NoteWriteAccessCheckReqDTO> requests) {
        if (requests == null || requests.isEmpty()) {
            return Collections.emptyList();
        }
        Response<List<NoteWriteAccessCheckReqDTO>> response = noteFeignApi.findWritableNoteAccesses(requests);
        if (response == null || !response.isSuccess()) {
            throw new IllegalStateException("笔记写权限鉴权服务调用失败");
        }
        return response.getData() == null ? Collections.emptyList() : response.getData();
    }

    /**
     * 同步校验单篇笔记是否允许当前用户写入（发表评论/点赞等）
     */
    public boolean isWritable(Long noteId, Long userId) {
        if (noteId == null || userId == null) {
            return false;
        }
        List<NoteWriteAccessCheckReqDTO> writable = findWritableNoteAccesses(
                List.of(NoteWriteAccessCheckReqDTO.builder().noteId(noteId).userId(userId).build()));
        return CollUtil.isNotEmpty(writable);
    }
}
