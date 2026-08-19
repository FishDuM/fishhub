package hk.ljx.fishhub.oss.biz.service;

import hk.ljx.fishhub.oss.biz.model.vo.PresignedUrlReqVO;
import hk.ljx.fishhub.oss.biz.model.vo.PresignedUrlRspVO;
import hk.ljx.framework.common.response.Response;
import org.springframework.web.multipart.MultipartFile;
import hk.ljx.fishhub.oss.dto.DeleteFileReqDTO;


public interface FileService {

    /**
     * 上传文件
     *
     * @param file
     * @return
     */
    Response<?> uploadFile(MultipartFile file);

    Response<?> deleteFile(DeleteFileReqDTO request);

    /**
     * 获取客户端直传预签名 URL
     */
    Response<PresignedUrlRspVO> getPresignedUrl(PresignedUrlReqVO request);
}
