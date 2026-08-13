package hk.ljx.fishhub.comment.biz.model.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PublishCommentReqVO {

    @NotNull(message = "笔记 ID 不能为空")
    private Long noteId;

    /**
     * 评论内容
     */
    @Size(max = 10000, message = "评论正文不能超过 10000 个字符")
    private String content;

    /**
     * 评论图片链接
     */
    private String imageUrl;

    /**
     * 回复的哪个评论（评论 ID）
     */
    private Long replyCommentId;

}
