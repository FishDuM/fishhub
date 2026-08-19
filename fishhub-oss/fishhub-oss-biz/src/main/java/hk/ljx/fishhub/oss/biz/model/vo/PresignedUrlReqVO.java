package hk.ljx.fishhub.oss.biz.model.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PresignedUrlReqVO {

    @NotBlank(message = "文件名不能为空")
    private String fileName;

    private String contentType;
}
