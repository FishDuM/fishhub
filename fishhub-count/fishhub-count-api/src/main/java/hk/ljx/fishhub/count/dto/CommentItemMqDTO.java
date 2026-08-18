package hk.ljx.fishhub.count.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评论变更事件条目。字段来自落库事实，保持确定性以便下游幂等。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CommentItemMqDTO {

    /**
     * 评论 ID
     */
    private Long id;

    /**
     * 所属笔记 ID
     */
    private Long noteId;

    /**
     * 评论级别：1 一级；2 二级
     */
    private Integer level;

    /**
     * 父评论 ID（一级评论为所属笔记 ID）
     */
    private Long parentId;

    /**
     * 发布者用户 ID
     */
    private Long userId;

    /**
     * 正文 UUID，可为空（无正文评论）
     */
    private String contentUuid;

    /**
     * 正文内容，仅发布事件携带
     */
    private String content;

    /**
     * 是否无正文
     */
    private Boolean isContentEmpty;

    /**
     * 评论时间
     */
    private LocalDateTime createTime;
}
