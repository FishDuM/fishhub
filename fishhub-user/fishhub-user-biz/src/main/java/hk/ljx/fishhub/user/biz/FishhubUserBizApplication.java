package hk.ljx.fishhub.user.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@MapperScan({"hk.ljx.fishhub.user.biz.domain.mapper", "hk.ljx.fishhub.user.relation.biz.domain.mapper"})
@EnableFeignClients(basePackages = "hk.ljx.fishhub")
@ComponentScan({"hk.ljx.fishhub.user", "hk.ljx.fishhub.oss", "hk.ljx.fishhub.count"})
public class FishhubUserBizApplication {

    public static void main(String[] args) {
        SpringApplication.run(FishhubUserBizApplication.class, args);
    }

}
