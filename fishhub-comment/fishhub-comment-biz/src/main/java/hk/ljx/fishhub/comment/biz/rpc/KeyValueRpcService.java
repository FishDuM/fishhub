package hk.ljx.fishhub.comment.biz.rpc;

import com.google.common.collect.Lists;
import hk.ljx.framework.common.constant.DateConstants;
import hk.ljx.fishhub.comment.biz.model.bo.CommentBO;
import hk.ljx.fishhub.kv.client.KeyValueClient;
import hk.ljx.fishhub.kv.dto.req.BatchAddCommentContentReqDTO;
import hk.ljx.fishhub.kv.dto.req.CommentContentReqDTO;
import hk.ljx.fishhub.kv.dto.req.DeleteCommentContentReqDTO;
import hk.ljx.fishhub.kv.dto.req.FindCommentContentReqDTO;
import hk.ljx.fishhub.kv.dto.rsp.FindCommentContentRspDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;


/**
 * 评论正文 KV 服务门面
 */
@Component
@RequiredArgsConstructor
public class KeyValueRpcService {

    private final KeyValueClient keyValueClient;

    /**
     * 批量存储评论内容
     *
     * @param commentBOS
     * @return
     */
    public boolean batchSaveCommentContent(List<CommentBO> commentBOS) {
        List<CommentContentReqDTO> comments = Lists.newArrayList();

        // BO 转 DTO
        commentBOS.forEach(commentBO -> {
            CommentContentReqDTO commentContentReqDTO = CommentContentReqDTO.builder()
                    .noteId(commentBO.getNoteId())
                    .content(commentBO.getContent())
                    .contentId(commentBO.getContentUuid())
                    .yearMonth(commentBO.getCreateTime().format(DateConstants.DATE_FORMAT_Y_M))
                    .build();
            comments.add(commentContentReqDTO);
        });

        // 构建接口入参实体类
        BatchAddCommentContentReqDTO batchAddCommentContentReqDTO = BatchAddCommentContentReqDTO.builder()
                .comments(comments)
                .build();

        // 调用 KV 存储服务
        boolean success = keyValueClient.batchAddCommentContent(batchAddCommentContentReqDTO);

        // 若失败，则主动抛出异常，以便调用层回滚事务
        if (!success) {
            throw new RuntimeException("批量保存评论内容失败");
        }

        return true;
    }

    /**
     * 批量查询评论内容
     *
     * @param noteId
     * @param findCommentContentReqDTOS
     * @return
     */
    public List<FindCommentContentRspDTO> batchFindCommentContent(Long noteId, List<FindCommentContentReqDTO> findCommentContentReqDTOS) {
        return keyValueClient.batchFindCommentContent(noteId, findCommentContentReqDTOS);
    }

    /**
     * 删除评论内容
     *
     * @param noteId
     * @param createTime
     * @param contentId
     * @return
     */
    public boolean deleteCommentContent(Long noteId, LocalDateTime createTime, String contentId) {
        DeleteCommentContentReqDTO deleteCommentContentReqDTO = DeleteCommentContentReqDTO.builder()
                .noteId(noteId)
                .yearMonth(DateConstants.DATE_FORMAT_Y_M.format(createTime))
                .contentId(contentId)
                .build();

        // 调用 KV 存储服务
        boolean success = keyValueClient.deleteCommentContent(deleteCommentContentReqDTO);

        if (!success) {
            throw new RuntimeException("删除评论内容失败");
        }

        return true;
    }
}
