package hk.ljx.fishhub.search.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "elasticsearch")
@Component
@Data
public class ElasticsearchProperties {
    private String address;

    /** IK 插件热更新词典的本地文件路径 */
    private String hotUpdateExtDict;
}
