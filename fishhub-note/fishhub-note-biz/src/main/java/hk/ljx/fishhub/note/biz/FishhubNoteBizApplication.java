package hk.ljx.fishhub.note.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@MapperScan("hk.ljx.fishhub.note.biz.domain.mapper")
@EnableFeignClients(basePackages = "hk.ljx")
public class FishhubNoteBizApplication {
    public static void main( String[] args ) {
        SpringApplication.run(FishhubNoteBizApplication.class, args);
    }
}
