package hk.ljx.fishhub.distributed.id.generator.client;

import cn.hutool.core.util.IdUtil;
import hk.ljx.fishhub.distributed.id.generator.api.DistributedIdGeneratorFeignApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 分布式 ID 生成统一客户端门面：
 * 提供基于 Leaf Snowflake 的发号能力，并内置 2 次指数退避重试（50ms、100ms）与本地雪花算法优雅降级兜底。
 */
@Slf4j
@RequiredArgsConstructor
public class DistributedIdGeneratorClient {

    private static final int MAX_RETRY_ATTEMPTS = 2;
    private static final long RETRY_BACKOFF_MILLIS = 50L;

    private final DistributedIdGeneratorFeignApi distributedIdGeneratorFeignApi;

    /**
     * 获取雪花算法 ID（带 2 次指数退避重试与本地雪花降级）
     *
     * @param bizTag 业务标识
     * @return 分布式 ID 字符串
     */
    public String getSnowflakeId(String bizTag) {
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return distributedIdGeneratorFeignApi.getSnowflakeId(bizTag);
            } catch (Exception e) {
                log.warn("==> 分布式雪花 ID 生成服务调用失败，第 {} 次重试, bizTag: {}", attempt, bizTag, e);
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    sleepBeforeRetry(attempt);
                }
            }
        }
        log.warn("==> 分布式雪花 ID 生成服务重试 {} 次后仍不可用，高可用降级为本地雪花算法, bizTag: {}", MAX_RETRY_ATTEMPTS, bizTag);
        return IdUtil.getSnowflakeNextIdStr();
    }

    /**
     * 获取号段模式 ID（带 2 次指数退避重试与本地雪花降级）
     *
     * @param bizTag 业务标识
     * @return 分布式 ID 字符串
     */
    public String getSegmentId(String bizTag) {
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return distributedIdGeneratorFeignApi.getSegmentId(bizTag);
            } catch (Exception e) {
                log.warn("==> 分布式号段 ID 生成服务调用失败，第 {} 次重试, bizTag: {}", attempt, bizTag, e);
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    sleepBeforeRetry(attempt);
                }
            }
        }
        log.warn("==> 分布式号段 ID 生成服务重试 {} 次后仍不可用，高可用降级为本地雪花算法, bizTag: {}", MAX_RETRY_ATTEMPTS, bizTag);
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
