package hk.ljx.fishhub.note.biz.rpc;

import cn.hutool.core.util.IdUtil;
import hk.ljx.fishhub.distributed.id.generator.api.DistributedIdGeneratorFeignApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DistributedIdGeneratorRpcService {

    private static final String BIZ_TAG_NOTE_ID = "leaf-snowflake-note-id";
    private static final int MAX_RETRY_ATTEMPTS = 2;
    private static final long RETRY_BACKOFF_MILLIS = 50L;

    @Resource
    private DistributedIdGeneratorFeignApi distributedIdGeneratorFeignApi;

    /**
     * 生成雪花算法 ID（带轻量重试与本地高可用降级）
     */
    public String getSnowflakeId() {
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return distributedIdGeneratorFeignApi.getSnowflakeId(BIZ_TAG_NOTE_ID);
            } catch (Exception e) {
                log.warn("==> ID 生成服务调用失败，第 {} 次重试, bizTag: {}", attempt, BIZ_TAG_NOTE_ID, e);
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_BACKOFF_MILLIS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        log.warn("==> ID 生成服务重试 {} 次后仍不可用，高可用降级为本地雪花算法生成 ID", MAX_RETRY_ATTEMPTS);
        return IdUtil.getSnowflakeNextIdStr();
    }
}
