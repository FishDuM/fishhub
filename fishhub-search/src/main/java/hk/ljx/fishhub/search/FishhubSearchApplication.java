package hk.ljx.fishhub.search;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("hk.ljx.fishhub.search.domain.mapper")
public class FishhubSearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(FishhubSearchApplication.class, args);
    }
}
