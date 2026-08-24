package hk.ljx.framework.id.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "fishhub.id")
public class IdGeneratorProperties {

    /**
     * 机器/工作节点 ID (0~31)，为空时基于 IP 自动计算
     */
    private Long workerId;

    /**
     * 数据中心 ID (0~31)
     */
    private Long datacenterId = 1L;
}
