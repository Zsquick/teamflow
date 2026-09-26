package com.teamflow.ratelimit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RequestRateLimiterTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.ofEpochMilli(70_123L),
            ZoneOffset.UTC
    );
    private static final RateLimitProperties.Policy POLICY =
            new RateLimitProperties.Policy(3, Duration.ofMinutes(1));

    private StringRedisTemplate redisTemplate;
    private RequestRateLimiter limiter;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        limiter = new RequestRateLimiter(redisTemplate, CLOCK);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldUseAtomicRedisCounterWithoutExposingSubject() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                any(),
                anyString()
        )).thenReturn(3L);

        assertDoesNotThrow(() -> limiter.check(
                "login",
                "203.0.113.8|private@example.com",
                POLICY
        ));

        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(
                any(RedisScript.class),
                keys.capture(),
                eq("120000")
        );
        String key = keys.getValue().getFirst();
        assertTrue(key.startsWith("teamflow:rate-limit:login:"));
        assertTrue(!key.contains("private@example.com"));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldRejectRequestBeyondLimitWithRetryAfter() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                any(),
                anyString()
        )).thenReturn(4L);

        RateLimitExceededException exception = assertThrows(
                RateLimitExceededException.class,
                () -> limiter.check("login", "203.0.113.8", POLICY)
        );

        assertEquals(50L, exception.retryAfterSeconds());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldFailOpenWhenRedisIsTemporarilyUnavailable() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                any(),
                anyString()
        )).thenThrow(new IllegalStateException("redis unavailable"));

        assertDoesNotThrow(() -> limiter.check(
                "registration",
                "203.0.113.8",
                POLICY
        ));
    }
}
