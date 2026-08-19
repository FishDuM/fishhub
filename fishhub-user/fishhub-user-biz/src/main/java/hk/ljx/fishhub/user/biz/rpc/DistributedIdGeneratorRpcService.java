package hk.ljx.fishhub.user.biz.rpc;

import cn.hutool.core.util.IdUtil;
import hk.ljx.fishhub.distributed.id.generator.api.DistributedIdGeneratorFeignApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 用户 ID / 小鱼号分布式发号服务：统一采用 Leaf Snowflake 算法，
 * 并具备 2 次退避重试与本地雪花算法高可用兜底。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedIdGeneratorRpcService {

    private static final int MAX_RETRY_ATTEMPTS = 2;
    private static final long RETRY_BACKOFF_MILLIS = 50L;

    private static final String FISHHUB_ID_PREFIX = "fish";

    private final DistributedIdGeneratorFeignApi distributedIdGeneratorFeignApi;

    private static final String BIZ_TAG_FISHHUB_ID = "leaf-snowflake-fishhub-id";
    private static final String BIZ_TAG_USER_ID = "leaf-snowflake-user-id";

    public String getFishhubId() {
        return FISHHUB_ID_PREFIX + callWithRetryAndFallback(
                () -> distributedIdGeneratorFeignApi.getSnowflakeId(BIZ_TAG_FISHHUB_ID), BIZ_TAG_FISHHUB_ID);
    }

    /**
     * 调用分布式 ID 生成服务用户 ID
     *
     * @return
     */
    public String getUserId() {
        return callWithRetryAndFallback(
                () -> distributedIdGeneratorFeignApi.getSnowflakeId(BIZ_TAG_USER_ID), BIZ_TAG_USER_ID);
    }

    private String callWithRetryAndFallback(Supplier<String> call, String bizTag) {
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return call.get();
            } catch (Exception e) {
                log.warn("==> ID 生成服务调用失败，进行第 {} 次重试, bizTag: {}", attempt, bizTag, e);
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    sleepBeforeRetry(attempt);
                }
            }
        }
        log.warn("==> ID 生成服务重试 {} 次后仍不可用，高可用降级为本地雪花算法, bizTag: {}", MAX_RETRY_ATTEMPTS, bizTag);
        return IdUtil.getSnowflakeNextIdStr();
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(RETRY_BACKOFF_MILLIS * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
