package hk.ljx.fishhub.kv.biz.config;

import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;


@Configuration
// RocketMQ Starter 2.2.3 仍使用旧式自动配置注册；Spring Boot 3 下需要显式导入。
@Import(RocketMQAutoConfiguration.class)
public class RocketMQConfig {
}
