package hk.ljx.fishhub.user.relation.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan(basePackages = "hk.ljx.fishhub.user.relation.biz.domain.mapper")
public class FishhubUserRelationBizApplication {
    public static void main( String[] args ) {
        SpringApplication.run(FishhubUserRelationBizApplication.class, args);
    }
}
