package hk.ljx.fishhub.user.relation.biz.model.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CheckFollowingReqVO {

    @NotNull(message = "目标用户 ID 不能为空")
    private Long targetUserId;
}
