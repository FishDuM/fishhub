package hk.ljx.fishhub.comment.biz.rpc;

import cn.hutool.core.collection.CollUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.note.api.NoteFeignApi;
import hk.ljx.fishhub.note.api.NoteWriteAccessCheckReqDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class NoteRpcService {

    private final NoteFeignApi noteFeignApi;

    /**
     * 笔记可访问状态本地短缓存（5 秒过期，极大降低评论翻页时的网络 RPC 延迟）
     */
    private static final Cache<Long, Boolean> NOTE_ACCESSIBLE_LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(1000)
            .maximumSize(10000)
            .expireAfterWrite(5, TimeUnit.SECONDS)
            .build();

    public static void invalidate(Long noteId) {
        if (noteId != null) {
            NOTE_ACCESSIBLE_LOCAL_CACHE.invalidate(noteId);
        }
    }

    public static void invalidateAll() {
        NOTE_ACCESSIBLE_LOCAL_CACHE.invalidateAll();
    }

    public boolean exists(Long noteId) {
        if (noteId == null) {
            return false;
        }
        Response<Boolean> response = noteFeignApi.exists(noteId);
        return response != null && response.isSuccess() && Boolean.TRUE.equals(response.getData());
    }

    public boolean isAccessible(Long noteId) {
        if (noteId == null) {
            return false;
        }
        Boolean cached = NOTE_ACCESSIBLE_LOCAL_CACHE.getIfPresent(noteId);
        if (cached != null) {
            return cached;
        }

        Response<Boolean> response = noteFeignApi.isAccessible(noteId);
        if (response == null || !response.isSuccess()) {
            throw new IllegalStateException("笔记访问鉴权服务调用失败");
        }
        boolean accessible = Boolean.TRUE.equals(response.getData());
        NOTE_ACCESSIBLE_LOCAL_CACHE.put(noteId, accessible);
        return accessible;
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
