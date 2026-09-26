package com.teamflow.integration;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Webhook 地址、密钥和超时安全边界测试。 */
class WebhookPropertiesTest {

    @Test
    void shouldAllowHttpsAndLoopbackHttp() {
        assertDoesNotThrow(() -> properties(
                true,
                "https://hooks.example.com/teamflow",
                "secret",
                Duration.ofSeconds(5)
        ));
        assertDoesNotThrow(() -> properties(
                false,
                "http://localhost:9090/events",
                null,
                Duration.ofSeconds(1)
        ));
    }

    @Test
    void shouldRejectInsecureRemoteEndpoint() {
        assertThrows(
                IllegalArgumentException.class,
                () -> properties(
                        false,
                        "http://hooks.example.com/events",
                        null,
                        Duration.ofSeconds(1)
                )
        );
    }

    @Test
    void shouldRequireSecretOnlyWhenEnabledAndPositiveTimeout() {
        assertThrows(
                IllegalArgumentException.class,
                () -> properties(
                        true,
                        "https://hooks.example.com/events",
                        " ",
                        Duration.ofSeconds(1)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> properties(
                        false,
                        "https://hooks.example.com/events",
                        null,
                        Duration.ZERO
                )
        );
    }

    private WebhookProperties properties(
            boolean enabled,
            String endpoint,
            String secret,
            Duration timeout
    ) {
        return new WebhookProperties(
                enabled,
                URI.create(endpoint),
                secret,
                timeout
        );
    }
}
