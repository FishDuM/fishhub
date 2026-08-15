package hk.ljx.fishhub.user.relation.biz.model.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindFansListReqVO {

    @NotNull(message = "查询用户 ID 不能为空")
    private Long userId;

    @Min(value = 0, message = "游标不能小于 0")
    private Long cursor = 0L;
}
