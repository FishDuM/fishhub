package hk.ljx.fishhub.comment.biz.model.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一级评论首条回复回填（批量）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CommentFirstReplyBO {
    /** 一级评论 ID */
    private Long id;

    /** 最早回复的评论 ID */
    private Long firstReplyCommentId;
}
