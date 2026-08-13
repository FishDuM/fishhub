package hk.ljx.fishhub.count.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("hk.ljx.fishhub.count.biz.domain.mapper")
public class FishhubCountBizApplication {

    public static void main(String[] args) {
        SpringApplication.run(FishhubCountBizApplication.class, args);
    }
}
