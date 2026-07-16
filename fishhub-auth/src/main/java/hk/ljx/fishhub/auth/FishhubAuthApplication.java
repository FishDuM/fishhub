package hk.ljx.fishhub.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("hk.ljx.fishhub.auth.domain.mapper")
public class FishhubAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(FishhubAuthApplication.class, args);
    }

}
