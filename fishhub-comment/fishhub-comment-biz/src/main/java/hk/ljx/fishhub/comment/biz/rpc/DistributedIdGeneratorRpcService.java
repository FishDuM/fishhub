package hk.ljx.fishhub.comment.biz.rpc;

import cn.hutool.core.util.IdUtil;
import hk.ljx.fishhub.distributed.id.generator.api.DistributedIdGeneratorFeignApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DistributedIdGeneratorRpcService {

    private static final String BIZ_TAG_COMMENT_ID = "leaf-segment-comment-id";

    @Resource
    private DistributedIdGeneratorFeignApi distributedIdGeneratorFeignApi;

    /**
     * 生成评论 ID
     */
    public String generateCommentId() {
        try {
            return distributedIdGeneratorFeignApi.getSegmentId(BIZ_TAG_COMMENT_ID);
        } catch (Exception e) {
            log.warn("==> ID 生成服务 RPC 不可用，使用本地雪花算法生成 ID", e);
            return IdUtil.getSnowflakeNextIdStr();
        }
    }
}
