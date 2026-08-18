package hk.ljx.fishhub.comment.biz.service;

import cn.hutool.core.collection.CollUtil;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentDOMapper;
import hk.ljx.fishhub.comment.biz.domain.mapper.CommentLikeDOMapper;
import hk.ljx.fishhub.comment.biz.enums.LikeUnlikeCommentTypeEnum;
import hk.ljx.fishhub.comment.biz.model.dto.LikeUnlikeCommentMqDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 评论点赞关系落库 + like_total 计数批量事务（t_comment.like_total 的唯一 DB 写入口）。
 *
 * <p>一次性提交一个批（≤30 条）：关系行批量 INSERT/DELETE，like_total 以「真实影响行数」计算增量，
 * 因此重复消费/同批重投天然幂等（重复行的 affected=0），且与 Redis 实时计数最终一致。</p>
 */
@Service
public class CommentLikePersistenceService {

    @Resource
    private CommentLikeDOMapper commentLikeDOMapper;
    @Resource
    private CommentDOMapper commentDOMapper;

    /**
     * 批量落库：按评论分组，同一评论内的 LIKE/UNLIKE 分别批量写关系行，
     * 并按 {@code inserted - deleted} 的真实影响行数更新 like_total。
     *
     * @param operations 一批点赞/取消点赞操作（已由外层完成同用户同评论的 last-op 合并）
     * @return 实际发生关系变化的评论 ID 集合
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
