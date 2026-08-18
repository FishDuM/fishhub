package hk.ljx.fishhub.comment.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 我的点赞足迹条目
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FindLikedCommentItemRspVO {

    /**
     * 评论 ID
     */
    private Long commentId;

    /**
     * 所属笔记 ID
     */
    private Long noteId;

    /**
     * 发布者用户 ID
     */
    private Long userId;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 评论图片
     */
    private String imageUrl;

    /**
     * 点赞时间（相对时间）
     */
    private String likeTime;

    /**
     * 被点赞数
     */
    private Long likeTotal;
}
