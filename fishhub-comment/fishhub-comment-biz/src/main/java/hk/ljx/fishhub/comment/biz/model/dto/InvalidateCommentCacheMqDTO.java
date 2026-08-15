package hk.ljx.fishhub.comment.biz.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 删除评论后远端派生缓存的失效任务。eventId 保证每次删除事实都有独立 outbox 记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvalidateCommentCacheMqDTO {

    private String eventId;

    private List<Long> deletedCommentIds;

    /**
     * 二级评论删除时的一级父评论 ID；一级评论删除时为空。
     */
    private Long parentCommentId;
}
