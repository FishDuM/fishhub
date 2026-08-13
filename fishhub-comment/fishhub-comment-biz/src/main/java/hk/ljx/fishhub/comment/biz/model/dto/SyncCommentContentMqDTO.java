package hk.ljx.fishhub.comment.biz.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SyncCommentContentMqDTO {

    /**
     * 正文所属评论。消费者据此确认评论尚未被删除。
     */
    private Long commentId;

    private Long noteId;

    private LocalDateTime createTime;

    private String contentUuid;

    private String content;
}
