package hk.ljx.fishhub.comment.biz.model.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 我的点赞足迹分页查询入参
 */
@Data
public class FindLikedCommentPageReqVO {

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码最小为 1")
    private Integer pageNo;

    /**
     * 每页条数（可选，默认 10，最大 50）
     */
    @Min(value = 1, message = "每页条数最小为 1")
    private Integer pageSize;
}
