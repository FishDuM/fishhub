package hk.ljx.fishhub.data.align;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("hk.ljx.fishhub.data.align.domain.mapper")
public class FishhubDataAlignApplication {

    public static void main(String[] args) {
        SpringApplication.run(FishhubDataAlignApplication.class, args);
    }

}