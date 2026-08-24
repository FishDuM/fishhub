package hk.ljx.framework.id.client;

import hk.ljx.framework.id.core.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 分布式发号客户端门面（本地嵌入式发号，纳秒级生成，零网络开销）
 */
@Slf4j
@RequiredArgsConstructor
public class DistributedIdGeneratorClient {

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    /**
     * 获取分布式雪花 ID
     *
     * @param bizTag 业务标识
     * @return 分布式 ID 字符串
     */
    public String getSnowflakeId(String bizTag) {
        return String.valueOf(snowflakeIdGenerator.nextId());
    }

    /**
     * 获取分布式雪花 ID（数值类型）
     *
     * @param bizTag 业务标识
     * @return 分布式 ID Long
     */
    public long nextId(String bizTag) {
        return snowflakeIdGenerator.nextId();
    }
}
