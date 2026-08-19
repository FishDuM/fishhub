package hk.ljx.fishhub.user.biz.rpc;

import hk.ljx.fishhub.distributed.id.generator.client.DistributedIdGeneratorClient;
import hk.ljx.fishhub.distributed.id.generator.constant.ApiConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 用户 ID / 小鱼号分布式发号服务：统一采用 Leaf Snowflake 算法
 */
@Component
@RequiredArgsConstructor
public class DistributedIdGeneratorRpcService {

    private static final String FISHHUB_ID_PREFIX = "fish";

    private final DistributedIdGeneratorClient distributedIdGeneratorClient;

    public String getFishhubId() {
        return FISHHUB_ID_PREFIX + distributedIdGeneratorClient.getSnowflakeId(ApiConstants.BIZ_TAG_FISHHUB_ID);
    }

    /**
     * 调用分布式 ID 生成服务生成用户 ID
     *
     * @return
     */
    public String getUserId() {
        return distributedIdGeneratorClient.getSnowflakeId(ApiConstants.BIZ_TAG_USER_ID);
    }
}
