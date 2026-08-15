package hk.ljx.fishhub.comment.biz.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一级评论缓存失效任务。eventId 用于让每次事实变更拥有独立的 outbox 幂等键。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvalidateOneLevelCommentCacheMqDTO {

    private String eventId;

    private Long noteId;
}
