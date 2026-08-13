package hk.ljx.fishhub.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import com.alibaba.csp.sentinel.adapter.gateway.sc.exception.SentinelGatewayBlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.datasource.ReadableDataSource;
import com.alibaba.csp.sentinel.datasource.nacos.NacosDataSource;
import com.alibaba.csp.sentinel.transport.config.TransportConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@Configuration
@Slf4j
public class SentinelGatewayConfig {

    private final List<ViewResolver> viewResolvers = new ArrayList<>();
    private final ServerCodecConfigurer serverCodecConfigurer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spring.cloud.sentinel.transport.dashboard:192.168.0.100:8858}")
    private String dashboard;
    @Value("${spring.cloud.sentinel.transport.port:8719}")
    private String apiPort;
    @Value("${spring.cloud.sentinel.datasource.nacos.server-addr:192.168.0.100:8848}")
    private String nacosServerAddr;
    @Value("${spring.cloud.sentinel.datasource.nacos.group-id:DEFAULT_GROUP}")
    private String nacosGroupId;
    @Value("${spring.cloud.sentinel.datasource.nacos.rule-data-id:fishhub-gateway-flow-rules}")
    private String nacosRuleDataId;

    public SentinelGatewayConfig(ServerCodecConfigurer serverCodecConfigurer) {
        this.serverCodecConfigurer = serverCodecConfigurer;
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SentinelGatewayBlockExceptionHandler sentinelGatewayBlockExceptionHandler() {
        // 自定义限流响应，返回 JSON 而非默认 HTML
        return new SentinelGatewayBlockExceptionHandler(viewResolvers, serverCodecConfigurer) {
            @Override
            public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
                if (!(ex instanceof BlockException)) {
                    return Mono.error(ex);
                }
                return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue("{\"code\":429,\"msg\":\"请求过于频繁，请稍后再试\"}"))
                        .flatMap(response -> response.writeTo(exchange, new ServerResponse.Context() {
                            @Override
                            public List<HttpMessageWriter<?>> messageWriters() {
                                return serverCodecConfigurer.getWriters();
                            }

                            @Override
                            public List<ViewResolver> viewResolvers() {
                                return viewResolvers;
                            }
                        }));
            }
        };
    }

    @Bean
    @Order(-1)
    public SentinelGatewayFilter sentinelGatewayFilter() {
        return new SentinelGatewayFilter();
    }

    @PostConstruct
    public void init() {
        // 配置 Sentinel 与 Dashboard 通信
        System.setProperty(TransportConfig.CONSOLE_SERVER, dashboard);
        System.setProperty(TransportConfig.SERVER_PORT, apiPort);

        // 默认规则（Nacos 中配置的规则会覆盖默认规则）
        loadGatewayRules();
        registerApiDefinitions();

        // 接入 Nacos 数据源，持久化网关限流规则
        initNacosDataSource();
    }

    /**
     * 默认限流规则：
     * 1. 登录接口 /auth/login 防爆破，QPS 10
     * 2. auth 路由整体保护，QPS 500
     */
    private void loadGatewayRules() {
        java.util.Set<GatewayFlowRule> rules = buildDefaultRules();
        GatewayRuleManager.loadRules(rules);
        log.info("Sentinel 网关默认限流规则已加载: {}", rules);
    }

    private java.util.Set<GatewayFlowRule> buildDefaultRules() {
        java.util.Set<GatewayFlowRule> rules = new java.util.HashSet<>();
        rules.add(new GatewayFlowRule("loginApi").setCount(10).setIntervalSec(1));
        rules.add(new GatewayFlowRule("auth").setCount(500).setIntervalSec(1));
        return rules;
    }

    private void registerApiDefinitions() {
        ApiDefinition loginApi = new ApiDefinition("loginApi")
                .setPredicateItems(Collections.singleton(
                        new ApiPathPredicateItem().setPattern("/auth/login")));
        GatewayApiDefinitionManager.loadApiDefinitions(Collections.singleton(loginApi));
    }

    private void initNacosDataSource() {
        try {
            ReadableDataSource<String, java.util.Set<GatewayFlowRule>> dataSource = new NacosDataSource<>(
                    nacosServerAddr, nacosGroupId, nacosRuleDataId, source -> {
                        if (StringUtils.isBlank(source)) {
                            return buildDefaultRules();
                        }
                        try {
                            return new java.util.HashSet<>(
                                    objectMapper.readValue(source, new TypeReference<List<GatewayFlowRule>>() {}));
                        } catch (Exception e) {
                            log.error("Sentinel 网关限流规则 JSON 解析失败", e);
                            return buildDefaultRules();
                        }
                    });
            // 确认数据源可读后再替换当前属性；Nacos 不可达时继续保留代码默认规则。
            dataSource.loadConfig();
            GatewayRuleManager.register2Property(dataSource.getProperty());
            log.info("Sentinel 网关限流规则已接入 Nacos 数据源: {}/{}/{}", nacosServerAddr, nacosGroupId, nacosRuleDataId);
        } catch (Exception e) {
            log.error("Sentinel 网关限流规则接入 Nacos 数据源失败，使用默认规则", e);
        }
    }

}
