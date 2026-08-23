package hk.ljx.fishhub.kv.biz.config;

import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.config.AbstractCassandraConfiguration;
import org.springframework.data.cassandra.config.SessionBuilderConfigurer;

import java.time.Duration;


@Configuration
public class CassandraConfig extends AbstractCassandraConfiguration {

    @Value("${spring.cassandra.keyspace-name}")
    private String keySpace;

    @Value("${spring.cassandra.contact-points}")
    private String contactPoints;

    @Value("${spring.cassandra.port}")
    private int port;

    @Value("${spring.cassandra.local-datacenter:datacenter1}")
    private String localDataCenter;

    @Override
    protected SessionBuilderConfigurer getSessionBuilderConfigurer() {
        return builder -> builder.withConfigLoader(
                DriverConfigLoader.programmaticBuilder()
                        .withDuration(DefaultDriverOption.CONNECTION_INIT_QUERY_TIMEOUT, Duration.ofSeconds(15))
                        .withDuration(DefaultDriverOption.CONTROL_CONNECTION_TIMEOUT, Duration.ofSeconds(15))
                        .withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofSeconds(15))
                        .build()
        );
    }

    /*
     * Provide a keyspace name to the configuration.
     */
    @Override
    public String getKeyspaceName() {
        return keySpace;
    }

    @Override
    public String getContactPoints() {
        return contactPoints;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public String getLocalDataCenter() {
        return localDataCenter;
    }
}
