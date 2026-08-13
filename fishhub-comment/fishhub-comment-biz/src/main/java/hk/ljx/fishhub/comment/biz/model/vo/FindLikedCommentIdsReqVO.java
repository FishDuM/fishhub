package hk.ljx.fishhub.comment.biz.model.vo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class FindLikedCommentIdsReqVO {

    @NotEmpty(message = "评论 ID 列表不能为空")
    @Size(max = 100, message = "一次最多查询 100 条评论")
    private List<Long> commentIds;
}
