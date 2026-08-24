package hk.ljx.fishhub.oss.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PresignedUrlRspVO {

    /**
     * 客户端直传 MinIO/OSS 的临时预签名 PUT URL（时效 10 分钟）
     */
    private String uploadUrl;

    /**
     * 上传成功后文件的永久公开/访问地址（写入业务表使用）
     */
    private String downloadUrl;
}
