package hk.ljx.fishhub.note.biz.rpc;

import cn.hutool.core.lang.Snowflake;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 笔记分布式发号服务（基于本地 Hutool Snowflake 纳秒级发号）
 */
@Component
@RequiredArgsConstructor
public class DistributedIdGeneratorRpcService {

    private final Snowflake snowflake;

    /**
     * 生成笔记雪花算法 ID (String)
     */
    public String getSnowflakeId() {
        return String.valueOf(snowflake.nextId());
    }

    /**
     * 生成笔记雪花算法 ID (Long)
     */
    public long nextId() {
        return snowflake.nextId();
    }
}
