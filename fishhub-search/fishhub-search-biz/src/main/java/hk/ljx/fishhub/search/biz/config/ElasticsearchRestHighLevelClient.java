package hk.ljx.fishhub.search.biz.config;

import lombok.RequiredArgsConstructor;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@RequiredArgsConstructor
public class ElasticsearchRestHighLevelClient {

    private final ElasticsearchProperties elasticsearchProperties;

    private static final String COLON = ":";
    private static final String HTTP = "http";

    @Bean
    public RestHighLevelClient restHighLevelClient() {
        String address = elasticsearchProperties.getAddress();

        String[] addressArr = address.split(COLON);
        // IP 地址
        String host = addressArr[0];
        // 端口
        int port = Integer.parseInt(addressArr[1]);

        HttpHost httpHost = new HttpHost(host, port, HTTP);

        // ES 停服/慢查询时尽快失败：显式配置请求超时与连接池
        return new RestHighLevelClient(RestClient.builder(httpHost)
                .setRequestConfigCallback(builder -> builder
                        .setConnectTimeout(1000)
                        .setSocketTimeout(3000)
                        .setConnectionRequestTimeout(1000))
                .setHttpClientConfigCallback(builder -> builder
                        .setMaxConnTotal(200)                 // 与 Tomcat 200 线程匹配
                        .setMaxConnPerRoute(100)));
    }
}
