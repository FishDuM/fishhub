package hk.ljx.fishhub.user.biz.rpc;

import hk.ljx.fishhub.distributed.id.generator.api.DistributedIdGeneratorFeignApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;


@Component
public class DistributedIdGeneratorRpcService {

    private static final String FISHHUB_ID_PREFIX = "fish";

    @Resource
    private DistributedIdGeneratorFeignApi distributedIdGeneratorFeignApi;

    
    private static final String BIZ_TAG_FISHHUB_ID = "leaf-segment-fishhub-id";

    /**
     * Leaf 号段模式：用户 ID 业务标识
     */
    private static final String BIZ_TAG_USER_ID = "leaf-segment-user-id";

    
    public String getFishhubId() {
        String segmentId = distributedIdGeneratorFeignApi.getSegmentId(BIZ_TAG_FISHHUB_ID);
        return FISHHUB_ID_PREFIX + segmentId;
    }

    /**
     * 调用分布式 ID 生成服务用户 ID
     *
     * @return
     */
    public String getUserId() {
        return distributedIdGeneratorFeignApi.getSegmentId(BIZ_TAG_USER_ID);
    }
}
