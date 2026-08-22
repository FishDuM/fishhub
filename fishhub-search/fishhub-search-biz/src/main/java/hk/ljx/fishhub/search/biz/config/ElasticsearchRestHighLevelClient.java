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

        // ES 停服/慢查询时尽快失败：显式配置请求超时与连接池。
        // socket 3s 对本地 Docker 转发 + 首次建索引过于紧张（实测单条写入 ~1.5s），放宽到 10s。
        return new RestHighLevelClient(RestClient.builder(httpHost)
                .setRequestConfigCallback(builder -> builder
                        .setConnectTimeout(2000)
                        .setSocketTimeout(10000)
                        .setConnectionRequestTimeout(2000))
                .setHttpClientConfigCallback(builder -> builder
                        .setMaxConnTotal(200)                 // 与 Tomcat 200 线程匹配
                        .setMaxConnPerRoute(100)));
    }
}
