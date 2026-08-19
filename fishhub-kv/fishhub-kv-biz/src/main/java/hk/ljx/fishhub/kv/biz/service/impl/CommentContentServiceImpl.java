package hk.ljx.fishhub.kv.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Lists;
import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.kv.biz.domain.dataobject.CommentContentDO;
import hk.ljx.fishhub.kv.biz.domain.dataobject.CommentContentPrimaryKey;
import hk.ljx.fishhub.kv.biz.domain.repository.CommentContentRepository;
import hk.ljx.fishhub.kv.biz.service.CommentContentService;
import hk.ljx.fishhub.kv.dto.req.BatchAddCommentContentReqDTO;
import hk.ljx.fishhub.kv.dto.req.BatchFindCommentContentReqDTO;
import hk.ljx.fishhub.kv.dto.req.DeleteCommentContentReqDTO;
import hk.ljx.fishhub.kv.dto.req.FindCommentContentReqDTO;
import hk.ljx.fishhub.kv.dto.rsp.FindCommentContentRspDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@Slf4j
public class CommentContentServiceImpl implements CommentContentService {

    @Resource
    private CassandraTemplate cassandraTemplate;
    @Resource
    private CommentContentRepository commentContentRepository;

    /**
     * 批量添加评论内容
     *
     * @param batchAddCommentContentReqDTO
     * @return
     */
    @Override
    public Response<?> batchAddCommentContent(BatchAddCommentContentReqDTO batchAddCommentContentReqDTO) {
        List<CommentContentDO> contentDOS = batchAddCommentContentReqDTO.getComments()
                .stream()
                .map(commentContentReqDTO -> {
                    // 主键
                    CommentContentPrimaryKey commentContentPrimaryKey = CommentContentPrimaryKey.builder()
                            .noteId(commentContentReqDTO.getNoteId())
                            .yearMonth(commentContentReqDTO.getYearMonth())
                            .contentId(UUID.fromString(commentContentReqDTO.getContentId()))
                            .build();

                    // DO 实体类
                    CommentContentDO commentContentDO = CommentContentDO.builder()
                            .primaryKey(commentContentPrimaryKey)
                            .content(commentContentReqDTO.getContent())
                            .build();

                    return commentContentDO;
                }).toList();

        // 批量插入
        cassandraTemplate.batchOps()
                .insert(contentDOS)
                .execute();

        return Response.success();
    }

    /**
     * 批量查询评论内容
     *
     * @param batchFindCommentContentReqDTO
     * @return
     */
    @Override
    public Response<?> batchFindCommentContent(BatchFindCommentContentReqDTO batchFindCommentContentReqDTO) {
        // 归属的笔记 ID
        Long noteId = batchFindCommentContentReqDTO.getNoteId();

        // 查询评论的发布年月、内容 UUID
        List<FindCommentContentReqDTO> commentContentKeys = batchFindCommentContentReqDTO.getCommentContentKeys();

        List<FindCommentContentRspDTO> findCommentContentRspDTOS = Lists.newArrayList();
        if (CollUtil.isNotEmpty(commentContentKeys)) {
            // 按 yearMonth 分组批量查询（过滤非法空值，防止 List.of(null) NPE）
            Map<String, List<FindCommentContentReqDTO>> byMonth = commentContentKeys.stream()
                    .filter(k -> k != null && StringUtils.isNotBlank(k.getYearMonth()) && StringUtils.isNotBlank(k.getContentId()))
                    .collect(Collectors.groupingBy(FindCommentContentReqDTO::getYearMonth));

            Map<String, CommentContentDO> foundById = new HashMap<>();
            for (Map.Entry<String, List<FindCommentContentReqDTO>> entry : byMonth.entrySet()) {
                List<UUID> contentIds = entry.getValue().stream()
                        .map(key -> {
                            try {
                                return UUID.fromString(key.getContentId().trim());
                            } catch (Exception e) {
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .toList();
                if (CollUtil.isEmpty(contentIds)) {
                    continue;
                }
                List<CommentContentDO> commentContentDOS = commentContentRepository
                        .findByPrimaryKeyNoteIdAndPrimaryKeyYearMonthInAndPrimaryKeyContentIdIn(
                                noteId, List.of(entry.getKey()), contentIds);
                for (CommentContentDO commentContentDO : commentContentDOS) {
                    // 键归一化为小写去重（C* 主键是规范小写 UUID，入参可能大写/带空格）
                    foundById.putIfAbsent(
                            normalizeContentId(commentContentDO.getPrimaryKey().getContentId().toString()),
                            commentContentDO);
                }
            }

            // 按入参顺序组装，缺失 key 跳过
            for (FindCommentContentReqDTO key : commentContentKeys) {
                if (key == null || key.getContentId() == null) continue;
                CommentContentDO commentContentDO = foundById.get(normalizeContentId(key.getContentId()));
                if (commentContentDO != null) {
                    findCommentContentRspDTOS.add(FindCommentContentRspDTO.builder()
                            .contentId(key.getContentId())
                            .content(commentContentDO.getContent())
                            .build());
                }
            }
        }

        return Response.success(findCommentContentRspDTOS);
    }

    private static String normalizeContentId(String contentId) {
        return contentId == null ? null : contentId.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 删除评论内容
     *
     * @param deleteCommentContentReqDTO
     * @return
     */
    @Override
    public Response<?> deleteCommentContent(DeleteCommentContentReqDTO deleteCommentContentReqDTO) {
        Long noteId = deleteCommentContentReqDTO.getNoteId();
        String yearMonth = deleteCommentContentReqDTO.getYearMonth();
        String contentId = deleteCommentContentReqDTO.getContentId();

        // 删除评论正文
        commentContentRepository.deleteByPrimaryKeyNoteIdAndPrimaryKeyYearMonthAndPrimaryKeyContentId(noteId, yearMonth, UUID.fromString(contentId));

        return Response.success();
    }
}
