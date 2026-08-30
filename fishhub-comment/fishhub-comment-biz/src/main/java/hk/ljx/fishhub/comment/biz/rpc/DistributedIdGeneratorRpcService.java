package hk.ljx.fishhub.comment.biz.rpc;

import cn.hutool.core.lang.Snowflake;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 评论分布式发号服务（基于本地 Hutool Snowflake 纳秒级发号）
 */
@Component
@RequiredArgsConstructor
public class DistributedIdGeneratorRpcService {

    private final Snowflake snowflake;

    /**
     * 生成评论 ID (String)
     */
    public String generateCommentId() {
        return String.valueOf(snowflake.nextId());
    }

    /**
     * 生成评论 ID (Long)
     */
    public long nextCommentId() {
        return snowflake.nextId();
    }
}
