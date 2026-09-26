package com.teamflow.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimitPropertiesTest {

    @Test
    void shouldKeepValidatedPolicies() {
        RateLimitProperties.Policy policy =
                new RateLimitProperties.Policy(10, Duration.ofMinutes(1));
        RateLimitProperties properties = new RateLimitProperties(
                true,
                policy,
                policy,
                policy,
                policy
        );

        assertEquals(10, properties.login().maxRequests());
        assertEquals(Duration.ofMinutes(1), properties.upload().window());
    }

    @Test
    void shouldRejectNonPositivePolicyValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RateLimitProperties.Policy(0, Duration.ofMinutes(1))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new RateLimitProperties.Policy(1, Duration.ZERO)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new RateLimitProperties.Policy(
                        1,
                        Duration.ofNanos(999_999)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new RateLimitProperties.Policy(
                        1,
                        Duration.ofMillis(Long.MAX_VALUE / 2L + 1L)
                )
        );
    }
}
