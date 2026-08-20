package hk.ljx.framework.redisson.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Redisson 独立客户端自动配置，支持单机与 Redis Cluster 分片集群模式。
 */
@AutoConfiguration
@ConditionalOnClass(RedissonClient.class)
public class RedissonAutoConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${spring.data.redis.database:0}") int database,
            @Value("${spring.data.redis.cluster.nodes:#{null}}") List<String> clusterNodes) {
        Config config = new Config();
        if (!CollectionUtils.isEmpty(clusterNodes)) {
            var clusterServers = config.useClusterServers();
            for (String node : clusterNodes) {
                clusterServers.addNodeAddress(node.startsWith("redis://") ? node : "redis://" + node);
            }
            if (StringUtils.hasText(password)) {
                clusterServers.setPassword(password);
            }
        } else {
            var singleServer = config.useSingleServer()
                    .setAddress("redis://" + host + ":" + port)
                    .setDatabase(database);
            if (StringUtils.hasText(password)) {
                singleServer.setPassword(password);
            }
        }
        return Redisson.create(config);
    }
}

