package hk.ljx.fishhub.oss.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeleteFileReqDTO {

    @NotBlank(message = "文件地址不能为空")
    private String fileUrl;
}
