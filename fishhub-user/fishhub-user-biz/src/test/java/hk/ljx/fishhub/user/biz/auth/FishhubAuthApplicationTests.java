package hk.ljx.fishhub.user.biz.auth;

import hk.ljx.fishhub.user.biz.auth.config.PasswordEncoderConfig;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FishhubUserBizApplicationTests {

    @Test
    void passwordEncoderCanVerifyEncodedPassword() {
        PasswordEncoder passwordEncoder = new PasswordEncoderConfig().passwordEncoder();
        String rawPassword = "123456";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        assertTrue(passwordEncoder.matches(rawPassword, encodedPassword));
    }
}
