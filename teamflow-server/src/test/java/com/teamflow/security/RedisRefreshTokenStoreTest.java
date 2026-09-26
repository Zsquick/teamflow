package com.teamflow.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Redis 刷新令牌存储测试。 */
@ExtendWith(MockitoExtension.class)
class RedisRefreshTokenStoreTest {

    private static final String USER_ID = "u001";
    private static final String KEY = "teamflow:refresh:u001";
    private static final String TOKEN = "signed.refresh.token";
    private static final String TOKEN_DIGEST =
            "66d957bc4e21f2809d429de833d5c28539bf155da24b76ff95e2286360e7b209";
    private static final String NEW_TOKEN = "new.signed.refresh.token";
    private static final String NEW_TOKEN_DIGEST =
            "813e27e4515a0e60a69d016ab2741c17910f5512c88b4e1bb5c24bd0d5d263c4";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisRefreshTokenStore tokenStore;

    @BeforeEach
    void setUp() {
        tokenStore = new RedisRefreshTokenStore(redisTemplate);
    }

    @Test
    void shouldSaveOnlyTokenDigestWithTtl() {
        Duration ttl = Duration.ofDays(7);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        tokenStore.save(USER_ID, TOKEN, ttl);

        verify(valueOperations).set(KEY, TOKEN_DIGEST, ttl);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldAtomicallyRotateMatchingTokenOnlyOnce() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of(KEY)),
                eq(TOKEN_DIGEST),
                eq(NEW_TOKEN_DIGEST),
                eq("604800000")
        )).thenReturn(1L, 0L);

        boolean firstResult = tokenStore.rotate(
                USER_ID,
                TOKEN,
                NEW_TOKEN,
                Duration.ofDays(7)
        );
        boolean secondResult = tokenStore.rotate(
                USER_ID,
                TOKEN,
                NEW_TOKEN,
                Duration.ofDays(7)
        );

        assertTrue(firstResult);
        assertFalse(secondResult);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldDeleteWithAtomicCompareAndDeleteScript() {
        ArgumentCaptor<RedisScript<Long>> scriptCaptor =
                ArgumentCaptor.forClass((Class) RedisScript.class);

        tokenStore.delete(USER_ID, TOKEN);

        verify(redisTemplate).execute(
                scriptCaptor.capture(),
                eq(List.of(KEY)),
                eq(TOKEN_DIGEST)
        );
        assertEquals(Long.class, scriptCaptor.getValue().getResultType());
        assertTrue(scriptCaptor.getValue().getScriptAsString()
                .contains("current == ARGV[1]"));
        assertTrue(scriptCaptor.getValue().getScriptAsString()
                .contains("redis.call('DEL', KEYS[1])"));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldPassBothDigestsAndMillisecondTtlToRotateScript() {
        ArgumentCaptor<RedisScript<Long>> scriptCaptor =
                ArgumentCaptor.forClass((Class) RedisScript.class);
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of(KEY)),
                eq(TOKEN_DIGEST),
                eq(NEW_TOKEN_DIGEST),
                eq("900000")
        )).thenReturn(1L);

        assertTrue(tokenStore.rotate(
                USER_ID,
                TOKEN,
                NEW_TOKEN,
                Duration.ofMinutes(15)
        ));

        verify(redisTemplate).execute(
                scriptCaptor.capture(),
                eq(List.of(KEY)),
                eq(TOKEN_DIGEST),
                eq(NEW_TOKEN_DIGEST),
                eq("900000")
        );
        assertEquals(Long.class, scriptCaptor.getValue().getResultType());
        assertTrue(scriptCaptor.getValue().getScriptAsString()
                .contains("'SET', KEYS[1], ARGV[2], 'PX', ARGV[3]"));
    }

    @Test
    void shouldPreserveTokenWhitespaceWhenCalculatingDigest() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        tokenStore.save(USER_ID, " " + TOKEN + " ", Duration.ofDays(1));

        verify(valueOperations).set(
                eq(KEY),
                org.mockito.ArgumentMatchers.argThat(
                        digest -> !TOKEN_DIGEST.equals(digest)
                                && digest.length() == 64
                ),
                eq(Duration.ofDays(1))
        );
    }

    @Test
    void shouldRejectInvalidInfrastructureArguments() {
        assertThrows(
                NullPointerException.class,
                () -> new RedisRefreshTokenStore(null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> tokenStore.rotate(
                        " ",
                        TOKEN,
                        NEW_TOKEN,
                        Duration.ofDays(1)
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> tokenStore.save(USER_ID, null, Duration.ofDays(1))
        );
        assertThrows(
                NullPointerException.class,
                () -> tokenStore.save(USER_ID, TOKEN, null)
        );
    }
}
