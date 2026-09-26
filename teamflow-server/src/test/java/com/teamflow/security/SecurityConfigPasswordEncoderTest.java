package com.teamflow.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** BCrypt 密码编码器配置测试。 */
class SecurityConfigPasswordEncoderTest {

    private final PasswordEncoder passwordEncoder =
            new SecurityConfig().passwordEncoder();

    @Test
    void shouldMatchOnlyTheOriginalPassword() {
        String encoded = passwordEncoder.encode("TeamFlow@2026");

        assertTrue(passwordEncoder.matches("TeamFlow@2026", encoded));
        assertFalse(passwordEncoder.matches("wrong-password", encoded));
    }

    @Test
    void shouldUseRandomSaltForEveryEncoding() {
        String first = passwordEncoder.encode("TeamFlow@2026");
        String second = passwordEncoder.encode("TeamFlow@2026");

        assertNotEquals(first, second);
        assertTrue(first.matches("\\$2[aby]\\$12\\$[./A-Za-z0-9]{53}"));
        assertTrue(second.matches("\\$2[aby]\\$12\\$[./A-Za-z0-9]{53}"));
    }
}
