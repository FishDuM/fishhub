package hk.ljx.fishhub.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "hk.ljx.fishhub")
public class FishhubAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(FishhubAuthApplication.class, args);
    }

}
