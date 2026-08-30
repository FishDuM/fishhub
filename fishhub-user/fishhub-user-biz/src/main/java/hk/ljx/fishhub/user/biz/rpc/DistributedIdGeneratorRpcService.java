package hk.ljx.fishhub.user.biz.rpc;

import cn.hutool.core.lang.Snowflake;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 用户 ID / 小鱼号分布式发号服务：统一采用本地 Hutool Snowflake 纳秒级算法
 */
@Component
@RequiredArgsConstructor
public class DistributedIdGeneratorRpcService {

    private static final String FISHHUB_ID_PREFIX = "fish";

    private final Snowflake snowflake;

    public String getFishhubId() {
        return FISHHUB_ID_PREFIX + snowflake.nextId();
    }

    /**
     * 调用本地 Snowflake 生成用户 ID
     *
     * @return 用户 ID 字符串
     */
    public String getUserId() {
        return String.valueOf(snowflake.nextId());
    }

    /**
     * 调用本地 Snowflake 生成用户 ID (Long)
     *
     * @return 用户 ID 数值
     */
    public long nextUserId() {
        return snowflake.nextId();
    }
}
