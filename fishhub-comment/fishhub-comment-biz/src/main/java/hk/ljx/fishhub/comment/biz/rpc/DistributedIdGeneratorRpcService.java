package hk.ljx.fishhub.comment.biz.rpc;

import cn.hutool.core.util.IdUtil;
import hk.ljx.fishhub.distributed.id.generator.api.DistributedIdGeneratorFeignApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DistributedIdGeneratorRpcService {

    private static final String BIZ_TAG_COMMENT_ID = "leaf-snowflake-comment-id";
    private static final int MAX_RETRY_ATTEMPTS = 2;
    private static final long RETRY_BACKOFF_MILLIS = 50L;

    @Resource
    private DistributedIdGeneratorFeignApi distributedIdGeneratorFeignApi;

    /**
     * 生成评论 ID（使用 Leaf 雪花算法，带重试与本地雪花降级）
     */
    public String generateCommentId() {
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return distributedIdGeneratorFeignApi.getSnowflakeId(BIZ_TAG_COMMENT_ID);
            } catch (Exception e) {
                log.warn("==> 评论 ID 生成服务调用失败，第 {} 次重试, bizTag: {}", attempt, BIZ_TAG_COMMENT_ID, e);
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_BACKOFF_MILLIS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        log.warn("==> 评论 ID 生成服务重试 {} 次后仍不可用，高可用降级为本地雪花算法", MAX_RETRY_ATTEMPTS);
        return IdUtil.getSnowflakeNextIdStr();
    }
}
