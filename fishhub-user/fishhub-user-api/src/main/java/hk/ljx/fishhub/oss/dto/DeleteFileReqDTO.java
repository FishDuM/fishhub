package hk.ljx.fishhub.oss.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeleteFileReqDTO {

    @NotBlank(message = "文件地址不能为空")
    private String fileUrl;

    /**
     * 文件归属用户 ID（服务间调用/管理员操作时必填；前台普通用户调用可不填）
     */
    private Long ownerId;
}
