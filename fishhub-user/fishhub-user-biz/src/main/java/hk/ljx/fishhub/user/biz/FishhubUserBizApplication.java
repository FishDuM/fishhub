package hk.ljx.fishhub.user.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@MapperScan("hk.ljx.fishhub.user.biz.domain.mapper")
@EnableFeignClients(basePackages = "hk.ljx.fishhub")
@ComponentScan({"hk.ljx.fishhub.user.biz", "hk.ljx.fishhub.count"}) //  多模块扫描
public class FishhubUserBizApplication {

    public static void main(String[] args) {
        SpringApplication.run(FishhubUserBizApplication.class, args);
    }

}
