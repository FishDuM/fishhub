package hk.ljx.fishhub.user.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@MapperScan("hk.ljx.fishhub.user.biz.domain.mapper")
@EnableFeignClients(basePackages = "hk.ljx")
public class FishhubUserBizApplication {
    public static void main( String[] args ) {
        SpringApplication.run(FishhubUserBizApplication.class, args);
    }
}
