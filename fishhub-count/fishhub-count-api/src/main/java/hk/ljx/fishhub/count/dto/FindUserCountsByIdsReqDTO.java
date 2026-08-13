package hk.ljx.fishhub.count.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FindUserCountsByIdsReqDTO {

    @NotEmpty(message = "用户 ID 列表不能为空")
    @Size(max = 100, message = "单次最多查询 100 个用户")
    private List<@NotNull(message = "用户 ID 不能为空") Long> userIds;
}
