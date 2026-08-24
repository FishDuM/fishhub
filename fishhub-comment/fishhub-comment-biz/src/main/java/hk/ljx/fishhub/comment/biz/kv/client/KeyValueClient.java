package hk.ljx.fishhub.comment.biz.kv.client;

import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Lists;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentContentDO;
import hk.ljx.fishhub.comment.biz.domain.dataobject.CommentContentPrimaryKey;
import hk.ljx.fishhub.comment.biz.domain.repository.CommentContentRepository;
import hk.ljx.fishhub.comment.biz.kv.dto.req.BatchAddCommentContentReqDTO;
import hk.ljx.fishhub.comment.biz.kv.dto.req.CommentContentReqDTO;
import hk.ljx.fishhub.comment.biz.kv.dto.req.DeleteCommentContentReqDTO;
import hk.ljx.fishhub.comment.biz.kv.dto.req.FindCommentContentReqDTO;
import hk.ljx.fishhub.comment.biz.kv.dto.rsp.FindCommentContentRspDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 评论正文存储本地组件（直连 Cassandra，消除跨进程 RPC 开销）
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KeyValueClient {

    private final CommentContentRepository commentContentRepository;

    public boolean batchAddCommentContent(BatchAddCommentContentReqDTO batchAddCommentContentReqDTO) {
        try {
            List<CommentContentReqDTO> comments = batchAddCommentContentReqDTO.getComments();
            List<CommentContentDO> commentContentDOS = Lists.newArrayList();
            comments.forEach(comment -> {
                CommentContentPrimaryKey primaryKey = CommentContentPrimaryKey.builder()
                        .noteId(comment.getNoteId())
                        .yearMonth(comment.getYearMonth())
                        .contentId(UUID.fromString(comment.getContentId()))
                        .build();
                commentContentDOS.add(CommentContentDO.builder()
                        .primaryKey(primaryKey)
                        .content(comment.getContent())
                        .build());
            });
            commentContentRepository.saveAll(commentContentDOS);
            return true;
        } catch (Exception e) {
            log.error("Cassandra 批量保存评论内容异常", e);
            return false;
        }
    }

    public List<FindCommentContentRspDTO> batchFindCommentContent(Long noteId, List<FindCommentContentReqDTO> findCommentContentReqDTOS) {
        if (CollUtil.isEmpty(findCommentContentReqDTOS)) {
            return Collections.emptyList();
        }
        try {
            List<CommentContentPrimaryKey> primaryKeys = Lists.newArrayList();
            findCommentContentReqDTOS.forEach(req -> {
                primaryKeys.add(CommentContentPrimaryKey.builder()
                        .noteId(noteId)
                        .yearMonth(req.getYearMonth())
                        .contentId(UUID.fromString(req.getContentId()))
                        .build());
            });
            Iterable<CommentContentDO> contentDOS = commentContentRepository.findAllById(primaryKeys);
            List<FindCommentContentRspDTO> rspList = Lists.newArrayList();
            if (contentDOS != null) {
                contentDOS.forEach(c -> rspList.add(FindCommentContentRspDTO.builder()
                        .contentId(c.getPrimaryKey().getContentId().toString())
                        .content(c.getContent())
                        .build()));
            }
            return rspList;
        } catch (Exception e) {
            log.error("Cassandra 批量查询评论内容异常, noteId={}", noteId, e);
            return Collections.emptyList();
        }
    }

    public boolean deleteCommentContent(DeleteCommentContentReqDTO req) {
        try {
            CommentContentPrimaryKey primaryKey = CommentContentPrimaryKey.builder()
                    .noteId(req.getNoteId())
                    .yearMonth(req.getYearMonth())
                    .contentId(UUID.fromString(req.getContentId()))
                    .build();
            commentContentRepository.deleteById(primaryKey);
            return true;
        } catch (Exception e) {
            log.error("Cassandra 删除评论内容异常", e);
            return false;
        }
    }
}
