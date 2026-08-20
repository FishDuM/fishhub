package hk.ljx.fishhub.count.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("hk.ljx.fishhub.count.biz.domain.mapper")
@EnableScheduling
@EnableFeignClients(basePackages = "hk.ljx.fishhub")
public class FishhubCountBizApplication {

    public static void main(String[] args) {
        SpringApplication.run(FishhubCountBizApplication.class, args);
    }
}
