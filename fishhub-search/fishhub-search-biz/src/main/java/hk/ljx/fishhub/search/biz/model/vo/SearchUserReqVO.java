package hk.ljx.fishhub.search.biz.model.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SearchUserReqVO {

    @NotBlank(message = "搜索关键词不能为空")
    private String keyword;

    @Min(value = 1, message = "页码不能小于 1")
    @Max(value = 100, message = "由于性能限制，最多仅支持查询前 100 页")
    @Builder.Default
    private Integer pageNo = 1; // 默认值为第一页

}
