package hk.ljx.fishhub.comment.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@MapperScan("hk.ljx.fishhub.comment.biz.domain.mapper")
@EnableRetry // 启用 Spring Retry
@EnableFeignClients(basePackages = "hk.ljx.fishhub")
public class FishhubCommentBizApplication {

    public static void main(String[] args) {
        SpringApplication.run(FishhubCommentBizApplication.class, args);
    }

}
