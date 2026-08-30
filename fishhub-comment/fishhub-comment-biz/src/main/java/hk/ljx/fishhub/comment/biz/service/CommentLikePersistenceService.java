package hk.ljx.fishhub.comment.biz.service;

import cn.hutool.core.collection.CollUtil;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentLikeDOMapper;
import hk.ljx.fishhub.comment.biz.enums.LikeUnlikeCommentTypeEnum;
import hk.ljx.fishhub.comment.biz.model.dto.LikeUnlikeCommentMqDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 评论点赞持久化服务
 */
@Service
@RequiredArgsConstructor
public class CommentLikePersistenceService {

    private final CommentLikeDOMapper commentLikeDOMapper;
    private final CommentDOMapper commentDOMapper;

    /**
     * 批量持久化点赞与取消点赞数据并更新计数
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> applyBatch(List<LikeUnlikeCommentMqDTO> operations) {
        if (CollUtil.isEmpty(operations)) {
            return List.of();
        }
        Map<Long, List<LikeUnlikeCommentMqDTO>> groups = operations.stream()
                .collect(Collectors.groupingBy(LikeUnlikeCommentMqDTO::getCommentId));

        List<Map.Entry<Long, List<LikeUnlikeCommentMqDTO>>> sortedGroups = groups.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();

        List<Long> appliedCommentIds = new ArrayList<>();
        for (Map.Entry<Long, List<LikeUnlikeCommentMqDTO>> entry : sortedGroups) {
            Long commentId = entry.getKey();
            List<LikeUnlikeCommentMqDTO> ops = entry.getValue();

            Map<Boolean, List<LikeUnlikeCommentMqDTO>> partitioned = ops.stream()
                    .collect(Collectors.partitioningBy(op -> Objects.equals(op.getType(), LikeUnlikeCommentTypeEnum.LIKE.getCode())));
            List<LikeUnlikeCommentMqDTO> likeOps = partitioned.get(Boolean.TRUE);
            List<LikeUnlikeCommentMqDTO> unlikeOps = partitioned.get(Boolean.FALSE);

            List<LikeUnlikeCommentMqDTO> sortedLikeOps = CollUtil.isEmpty(likeOps) ? null : likeOps.stream()
                    .sorted(java.util.Comparator.comparing(LikeUnlikeCommentMqDTO::getCommentId)
                            .thenComparing(LikeUnlikeCommentMqDTO::getUserId))
                    .toList();
            List<LikeUnlikeCommentMqDTO> sortedUnlikeOps = CollUtil.isEmpty(unlikeOps) ? null : unlikeOps.stream()
                    .sorted(java.util.Comparator.comparing(LikeUnlikeCommentMqDTO::getCommentId)
                            .thenComparing(LikeUnlikeCommentMqDTO::getUserId))
                    .toList();

            int inserted = CollUtil.isEmpty(sortedLikeOps) ? 0 : commentLikeDOMapper.batchInsert(sortedLikeOps);
            int deleted = CollUtil.isEmpty(sortedUnlikeOps) ? 0 : commentLikeDOMapper.batchDelete(sortedUnlikeOps);
            int delta = inserted - deleted;
            if (delta != 0) {
                commentDOMapper.updateLikeTotalByCommentId(delta, commentId);
            }
            if (inserted > 0 || deleted > 0) {
                appliedCommentIds.add(commentId);
            }
        }
        return appliedCommentIds;
    }
}
