package hk.ljx.fishhub.user.biz.rpc;

import hk.ljx.framework.common.exception.BizException;
import hk.ljx.fishhub.distributed.id.generator.api.DistributedIdGeneratorFeignApi;
import hk.ljx.fishhub.user.biz.enums.ResponseCodeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;


/**
 * Leaf 号段模式带本地 segment buffer 预取：ID 服务短暂宕机时只要缓冲有剩余仍可发号，
 * 故障是"延迟暴露"的，因此这里做有限重试即可覆盖绝大多数抖动场景。
 */
@Slf4j
@Component
public class DistributedIdGeneratorRpcService {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_BACKOFF_MILLIS = 100L;

    private static final String FISHHUB_ID_PREFIX = "fish";

    @Resource
    private DistributedIdGeneratorFeignApi distributedIdGeneratorFeignApi;

    
    private static final String BIZ_TAG_FISHHUB_ID = "leaf-segment-fishhub-id";

    /**
     * Leaf 号段模式：用户 ID 业务标识
     */
    private static final String BIZ_TAG_USER_ID = "leaf-segment-user-id";

    
    public String getFishhubId() {
        return FISHHUB_ID_PREFIX + callWithRetry(
                () -> distributedIdGeneratorFeignApi.getSegmentId(BIZ_TAG_FISHHUB_ID), BIZ_TAG_FISHHUB_ID);
    }

    /**
     * 调用分布式 ID 生成服务用户 ID
     *
     * @return
     */
    public String getUserId() {
        return callWithRetry(() -> distributedIdGeneratorFeignApi.getSegmentId(BIZ_TAG_USER_ID), BIZ_TAG_USER_ID);
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
