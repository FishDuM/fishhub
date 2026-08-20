package hk.ljx.fishhub.kv.biz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "hk.ljx.fishhub")
public class FishhubKVBizApplication {

    public static void main(String[] args) {
        SpringApplication.run(FishhubKVBizApplication.class, args);
    }

}
