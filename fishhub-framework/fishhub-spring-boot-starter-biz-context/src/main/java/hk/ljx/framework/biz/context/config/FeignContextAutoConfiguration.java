package hk.ljx.framework.biz.context.config;

import hk.ljx.framework.biz.context.interceptor.FeignRequestInterceptor;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

@AutoConfiguration
public class FeignContextAutoConfiguration {

    @Bean
    public FeignRequestInterceptor feignRequestInterceptor() {
        return new FeignRequestInterceptor();
    }

    /**
     * OpenFeign 使用的 Apache HttpClient 5 连接池客户端。
     * 最大连接数 2000，单路由 500，长连接保活 900 秒。
     */
    @Bean
    @ConditionalOnClass(CloseableHttpClient.class)
    @ConditionalOnMissingBean(CloseableHttpClient.class)
    public CloseableHttpClient feignHttpClient5() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(2000);
        connectionManager.setDefaultMaxPerRoute(500);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.of(2000, TimeUnit.MILLISECONDS))
                .setResponseTimeout(Timeout.of(10000, TimeUnit.MILLISECONDS))
                .build();

        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.of(60, TimeUnit.SECONDS))
                .build();
    }
}


