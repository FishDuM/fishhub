package hk.ljx.fishhub.user.relation.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@MapperScan(basePackages = "hk.ljx.fishhub.user.relation.biz.domain.mapper")
@EnableFeignClients(basePackages = "hk.ljx")
public class FishhubUserRelationBizApplication {
    public static void main( String[] args ) {
        SpringApplication.run(FishhubUserRelationBizApplication.class, args);
    }
}
