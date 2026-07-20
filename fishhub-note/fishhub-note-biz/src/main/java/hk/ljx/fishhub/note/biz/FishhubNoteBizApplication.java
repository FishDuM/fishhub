package hk.ljx.fishhub.note.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("hk.ljx.fishhub.note.biz.domain.mapper")
public class FishhubNoteBizApplication {
    public static void main( String[] args ) {
        SpringApplication.run(FishhubNoteBizApplication.class, args);
    }
}
