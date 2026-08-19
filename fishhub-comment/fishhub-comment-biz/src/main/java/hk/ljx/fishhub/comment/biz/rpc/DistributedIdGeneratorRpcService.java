package hk.ljx.fishhub.comment.biz.rpc;

import hk.ljx.fishhub.distributed.id.generator.client.DistributedIdGeneratorClient;
import hk.ljx.fishhub.distributed.id.generator.constant.ApiConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DistributedIdGeneratorRpcService {

    private final DistributedIdGeneratorClient distributedIdGeneratorClient;

    /**
     * 生成评论 ID（使用 Leaf 雪花算法，带重试与本地雪花降级）
     */
    public String generateCommentId() {
        return distributedIdGeneratorClient.getSnowflakeId(ApiConstants.BIZ_TAG_COMMENT_ID);
    }
}
