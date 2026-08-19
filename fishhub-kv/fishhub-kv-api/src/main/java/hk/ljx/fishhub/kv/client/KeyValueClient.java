package hk.ljx.fishhub.kv.client;

import cn.hutool.core.collection.CollUtil;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.kv.api.KeyValueFeignApi;
import hk.ljx.fishhub.kv.dto.req.AddNoteContentReqDTO;
import hk.ljx.fishhub.kv.dto.req.BatchAddCommentContentReqDTO;
import hk.ljx.fishhub.kv.dto.req.BatchFindCommentContentReqDTO;
import hk.ljx.fishhub.kv.dto.req.DeleteCommentContentReqDTO;
import hk.ljx.fishhub.kv.dto.req.DeleteNoteContentReqDTO;
import hk.ljx.fishhub.kv.dto.req.FindCommentContentReqDTO;
import hk.ljx.fishhub.kv.dto.req.FindNoteContentReqDTO;
import hk.ljx.fishhub.kv.dto.rsp.FindCommentContentRspDTO;
import hk.ljx.fishhub.kv.dto.rsp.FindNoteContentRspDTO;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * KV 存储服务 RPC 客户端
 */
@RequiredArgsConstructor
public class KeyValueClient {

    private final KeyValueFeignApi keyValueFeignApi;

    /**
     * 保存笔记内容
     */
    public boolean saveNoteContent(String uuid, String content) {
        AddNoteContentReqDTO addNoteContentReqDTO = new AddNoteContentReqDTO();
        addNoteContentReqDTO.setUuid(uuid);
        addNoteContentReqDTO.setContent(content);

        Response<?> response = keyValueFeignApi.addNoteContent(addNoteContentReqDTO);

        return Objects.nonNull(response) && response.isSuccess();
    }

    /**
     * 删除笔记内容
     */
    public boolean deleteNoteContent(String uuid) {
        DeleteNoteContentReqDTO deleteNoteContentReqDTO = new DeleteNoteContentReqDTO();
        deleteNoteContentReqDTO.setUuid(uuid);

        Response<?> response = keyValueFeignApi.deleteNoteContent(deleteNoteContentReqDTO);

        return Objects.nonNull(response) && response.isSuccess();
    }

    /**
     * 查询笔记内容
     */
    public String findNoteContent(String uuid) {
        FindNoteContentReqDTO findNoteContentReqDTO = new FindNoteContentReqDTO();
        findNoteContentReqDTO.setUuid(uuid);

        Response<FindNoteContentRspDTO> response = keyValueFeignApi.findNoteContent(findNoteContentReqDTO);

        if (Objects.isNull(response) || !response.isSuccess() || Objects.isNull(response.getData())) {
            return null;
        }

        return response.getData().getContent();
    }

    /**
     * 批量存储评论内容
     */
    public boolean batchAddCommentContent(BatchAddCommentContentReqDTO batchAddCommentContentReqDTO) {
        Response<?> response = keyValueFeignApi.batchAddCommentContent(batchAddCommentContentReqDTO);

        return Objects.nonNull(response) && response.isSuccess();
    }

    /**
     * 批量查询评论内容
     */
    public List<FindCommentContentRspDTO> batchFindCommentContent(Long noteId, List<FindCommentContentReqDTO> findCommentContentReqDTOS) {
        BatchFindCommentContentReqDTO batchFindCommentContentReqDTO = BatchFindCommentContentReqDTO.builder()
                .noteId(noteId)
                .commentContentKeys(findCommentContentReqDTOS)
                .build();

        Response<List<FindCommentContentRspDTO>> response = keyValueFeignApi.batchFindCommentContent(batchFindCommentContentReqDTO);

        if (Objects.isNull(response) || !response.isSuccess() || CollUtil.isEmpty(response.getData())) {
            return Collections.emptyList();
        }

        return response.getData();
    }

    /**
     * 删除评论内容
     */
    public boolean deleteCommentContent(DeleteCommentContentReqDTO deleteCommentContentReqDTO) {
        Response<?> response = keyValueFeignApi.deleteCommentContent(deleteCommentContentReqDTO);

        return Objects.nonNull(response) && response.isSuccess();
    }
}
