package hk.ljx.fishhub.search.biz.canal;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


@ConfigurationProperties(prefix = CanalProperties.PREFIX)
@Component
@Data
public class CanalProperties {

    public static final String PREFIX = "canal";

    /**
     * 同步模式: mq (默认推荐，RocketMQ 消息模式) / tcp (单机直连轮询模式)
     */
    private String mode = "mq";

    /**
     * Canal RocketMQ 相关配置
     */
    private Mq mq = new Mq();

    @Data
    public static class Mq {
        /**
         * 消费的主题 Topic
         */
        private String topic = "fishhub_canal_topic";

        /**
         * 消费者组 Group
         */
        private String group = "fishhub_group_search_canal";
    }

    /**
     * Canal 链接地址 (TCP 模式使用)
     */
    private String address;

    /**
     * 数据目标
     */
    private String destination;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 订阅规则
     */
    private String subscribe = "fishhub\\.t_(note|user|note_count|user_count)";

    /**
     * 一批次拉取数据 (TCP 模式使用)
     */
    private int batchSize = 1000;
}
