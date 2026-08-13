package hk.ljx.fishhub.auth;

import hk.ljx.fishhub.auth.config.PasswordEncoderConfig;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FishhubAuthApplicationTests {

    @Test
    void passwordEncoderCanVerifyEncodedPassword() {
        PasswordEncoder passwordEncoder = new PasswordEncoderConfig().passwordEncoder();
        String rawPassword = "123456";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        assertTrue(passwordEncoder.matches(rawPassword, encodedPassword));
    }
}
