package hk.ljx.fishhub.comment.biz.rpc;

import hk.ljx.framework.common.exception.BizException;
import hk.ljx.fishhub.comment.biz.enums.ResponseCodeEnum;
import hk.ljx.fishhub.distributed.id.generator.api.DistributedIdGeneratorFeignApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;


/**
 * ID 生成服务调用方韧性：有限重试 + 快速失败，覆盖 ID 服务的短暂抖动。
 */
@Slf4j
@Component
public class DistributedIdGeneratorRpcService {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_BACKOFF_MILLIS = 100L;

    private static final String BIZ_TAG_COMMENT_ID = "leaf-segment-comment-id";

    @Resource
    private DistributedIdGeneratorFeignApi distributedIdGeneratorFeignApi;

    /**
     * 生成评论 ID
     *
     * @return
     */
    public String generateCommentId() {
        return callWithRetry(() -> distributedIdGeneratorFeignApi.getSegmentId(BIZ_TAG_COMMENT_ID), BIZ_TAG_COMMENT_ID);
    }

    private String callWithRetry(Supplier<String> call, String bizTag) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return call.get();
            } catch (RuntimeException e) {
                lastFailure = e;
                if (attempt == MAX_RETRY_ATTEMPTS) {
                    break;
                }
                log.warn("==> ID 生成服务调用失败，进行第 {} 次重试, bizTag: {}", attempt, bizTag, e);
                sleepBeforeRetry(attempt);
            }
        }
        log.error("==> ID 生成服务重试 {} 次后仍失败, bizTag: {}", MAX_RETRY_ATTEMPTS, bizTag, lastFailure);
        throw new BizException(ResponseCodeEnum.SYSTEM_ERROR);
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(RETRY_BACKOFF_MILLIS * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
