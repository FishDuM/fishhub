package hk.ljx.fishhub.user.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("hk.ljx.fishhub.user.biz.domain.mapper")
public class FishhubUserBizApplication {
    public static void main( String[] args ) {
        SpringApplication.run(FishhubUserBizApplication.class, args);
    }
}
