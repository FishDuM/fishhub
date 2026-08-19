package hk.ljx.fishhub.oss.biz.strategy;

import hk.ljx.fishhub.oss.biz.model.vo.PresignedUrlRspVO;
import org.springframework.web.multipart.MultipartFile;


public interface FileStrategy {

    /**
     * 文件上传
     *
     * @param file
     * @param bucketName
     * @return
     */
    String uploadFile(MultipartFile file, String bucketName, Long ownerId);

    void deleteFile(String fileUrl, String bucketName, Long ownerId);

    /**
     * 获取客户端直传预签名 URL
     */
    PresignedUrlRspVO getPresignedUploadUrl(String fileName, String contentType, String bucketName, Long ownerId);

}
