package com.teamflow.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Redis 7.4 中刷新令牌摘要、TTL 和 Lua 原子轮换的集成测试。 */
@Testcontainers(disabledWithoutDocker = true)
class RedisRefreshTokenStoreIntegrationTest {

    private static final int REDIS_PORT = 6379;
    private static final String USER_ID = "u001";
    private static final String KEY = "teamflow:refresh:" + USER_ID;
    private static final String CURRENT_TOKEN = "current.refresh.token";
    private static final Duration TTL = Duration.ofMinutes(5);

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(
                    com.teamflow.integration.TestContainerImages.REDIS
            )
                    .withExposedPorts(REDIS_PORT);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RedisRefreshTokenStore tokenStore;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(
                REDIS.getHost(),
                REDIS.getMappedPort(REDIS_PORT)
        );
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        flushDatabase();
        tokenStore = new RedisRefreshTokenStore(redisTemplate);
    }

    @AfterEach
    void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void shouldStoreOnlyDigestAndEnforceCompareRotateDeleteLifecycle() {
        tokenStore.save(USER_ID, CURRENT_TOKEN, TTL);

        String storedDigest = redisTemplate.opsForValue().get(KEY);
        Long remainingMillis = redisTemplate.getExpire(
                KEY,
                TimeUnit.MILLISECONDS
        );
        assertNotNull(storedDigest);
        assertNotEquals(CURRENT_TOKEN, storedDigest);
        assertEquals(64, storedDigest.length());
        assertNotNull(remainingMillis);
        assertTrue(remainingMillis > 0);
        assertTrue(remainingMillis <= TTL.toMillis());

        assertFalse(tokenStore.rotate(
                USER_ID,
                "wrong.refresh.token",
                "unused.refresh.token",
                TTL
        ));
        assertEquals(storedDigest, redisTemplate.opsForValue().get(KEY));

        String nextToken = "next.refresh.token";
        assertTrue(tokenStore.rotate(
                USER_ID,
                CURRENT_TOKEN,
                nextToken,
                TTL
        ));
        assertNotEquals(storedDigest, redisTemplate.opsForValue().get(KEY));
        assertFalse(tokenStore.rotate(
                USER_ID,
                CURRENT_TOKEN,
                "replayed.refresh.token",
                TTL
        ));

        tokenStore.delete(USER_ID, CURRENT_TOKEN);
        assertNotNull(redisTemplate.opsForValue().get(KEY));
        tokenStore.delete(USER_ID, nextToken);
        assertNull(redisTemplate.opsForValue().get(KEY));
    }

    @Test
    void shouldAllowOnlyOneConcurrentRotationOfTheSameToken()
            throws Exception {
        tokenStore.save(USER_ID, CURRENT_TOKEN, TTL);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = List.of(
                    executor.submit(() -> rotateAfterSignal(
                            ready,
                            start,
                            "next.refresh.token.a"
                    )),
                    executor.submit(() -> rotateAfterSignal(
                            ready,
                            start,
                            "next.refresh.token.b"
                    ))
            );

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            long successCount = 0;
            for (Future<Boolean> result : results) {
                if (result.get(5, TimeUnit.SECONDS)) {
                    successCount++;
                }
            }
            assertEquals(1, successCount);
        }
    }

    private boolean rotateAfterSignal(
            CountDownLatch ready,
            CountDownLatch start,
            String nextToken
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("并发轮换未能同时开始");
        }
        return tokenStore.rotate(
                USER_ID,
                CURRENT_TOKEN,
                nextToken,
                TTL
        );
    }

    private void flushDatabase() {
        RedisConnection connection = connectionFactory.getConnection();
        try {
            connection.serverCommands().flushDb();
        } finally {
            connection.close();
        }
    }
}
