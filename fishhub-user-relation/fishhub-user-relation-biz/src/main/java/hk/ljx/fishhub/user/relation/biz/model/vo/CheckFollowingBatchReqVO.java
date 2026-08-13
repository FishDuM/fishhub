package hk.ljx.fishhub.user.relation.biz.model.vo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CheckFollowingBatchReqVO {

    @NotEmpty(message = "目标用户 ID 不能为空")
    @Size(max = 100, message = "单次最多查询 100 个用户")
    private List<Long> targetUserIds;
}
