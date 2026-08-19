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

        List<Long> appliedCommentIds = new ArrayList<>();
        for (Map.Entry<Long, List<LikeUnlikeCommentMqDTO>> entry : groups.entrySet()) {
            Long commentId = entry.getKey();
            List<LikeUnlikeCommentMqDTO> ops = entry.getValue();

            List<LikeUnlikeCommentMqDTO> likeOps = ops.stream()
                    .filter(op -> Objects.equals(op.getType(), LikeUnlikeCommentTypeEnum.LIKE.getCode()))
                    .toList();
            List<LikeUnlikeCommentMqDTO> unlikeOps = ops.stream()
                    .filter(op -> Objects.equals(op.getType(), LikeUnlikeCommentTypeEnum.UNLIKE.getCode()))
                    .toList();

            int inserted = CollUtil.isEmpty(likeOps) ? 0 : commentLikeDOMapper.batchInsert(likeOps);
            int deleted = CollUtil.isEmpty(unlikeOps) ? 0 : commentLikeDOMapper.batchDelete(unlikeOps);
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
