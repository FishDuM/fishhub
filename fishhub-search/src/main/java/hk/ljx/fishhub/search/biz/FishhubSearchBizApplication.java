package hk.ljx.fishhub.search.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("hk.ljx.fishhub.search.biz.domain.mapper")
public class FishhubSearchBizApplication {

    public static void main(String[] args) {
        SpringApplication.run(FishhubSearchBizApplication.class, args);
    }

}
