package hk.ljx.fishhub.data.align.rpc;

import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.search.api.SearchFeignApi;
import hk.ljx.fishhub.search.dto.req.RebuildNoteDocumentReqDTO;
import hk.ljx.fishhub.search.dto.req.RebuildUserDocumentReqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;



@Component
@Slf4j
public class SearchRpcService {

    @Resource
    private SearchFeignApi searchFeignApi;

    /**
     * 调用重建笔记文档接口。失败时抛出异常，保留临时对账记录供下次重试。
     * @param noteId
     */
    public void rebuildNoteDocument(Long noteId) {
        RebuildNoteDocumentReqDTO rebuildNoteDocumentReqDTO = RebuildNoteDocumentReqDTO.builder()
                .id(noteId)
                .build();

        try {
            Response<?> response = searchFeignApi.rebuildNoteDocument(rebuildNoteDocumentReqDTO);
            if (response == null || !response.isSuccess()) {
                throw new IllegalStateException("重建笔记索引失败, noteId=" + noteId + ", response=" + response);
            }
        } catch (Exception e) {
            log.error("重建笔记索引异常, noteId={}", noteId, e);
            throw new IllegalStateException("重建笔记索引异常, noteId=" + noteId, e);
        }
    }

    /**
     * 调用重建用户文档接口。失败时抛出异常，保留临时对账记录供下次重试。
     * @param userId
     */
    public void rebuildUserDocument(Long userId) {
        RebuildUserDocumentReqDTO rebuildUserDocumentReqDTO = RebuildUserDocumentReqDTO.builder()
                .id(userId)
                .build();

        try {
            Response<?> response = searchFeignApi.rebuildUserDocument(rebuildUserDocumentReqDTO);
            if (response == null || !response.isSuccess()) {
                throw new IllegalStateException("重建用户索引失败, userId=" + userId + ", response=" + response);
            }
        } catch (Exception e) {
            log.error("重建用户索引异常, userId={}", userId, e);
            throw new IllegalStateException("重建用户索引异常, userId=" + userId, e);
        }
    }

}
