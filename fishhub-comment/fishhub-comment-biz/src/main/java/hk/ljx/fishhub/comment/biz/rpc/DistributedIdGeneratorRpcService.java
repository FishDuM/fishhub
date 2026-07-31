package hk.ljx.fishhub.comment.biz.rpc;

import hk.ljx.fishhub.distributed.id.generator.api.DistributedIdGeneratorFeignApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;


@Component
public class DistributedIdGeneratorRpcService {

    @Resource
    private DistributedIdGeneratorFeignApi distributedIdGeneratorFeignApi;

    /**
     * 生成评论 ID
     *
     * @return
     */
    public String generateCommentId() {
        return distributedIdGeneratorFeignApi.getSegmentId("leaf-segment-comment-id");
    }

}
