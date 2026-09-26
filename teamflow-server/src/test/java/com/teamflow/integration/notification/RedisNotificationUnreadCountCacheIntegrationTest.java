package com.teamflow.integration.notification;

import com.teamflow.notification.NotificationCacheProperties;
import com.teamflow.notification.RedisNotificationUnreadCountCache;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用真实 Redis 7.4 验证通知未读数、TTL、清除与脏值降级。 */
@Testcontainers(disabledWithoutDocker = true)
class RedisNotificationUnreadCountCacheIntegrationTest {

    private static final String KEY = "teamflow:notification:unread:u001";

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(
                    com.teamflow.integration.TestContainerImages.REDIS
            )
                    .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static RedisNotificationUnreadCountCache cache;

    @BeforeAll
    static void createClient() {
        RedisStandaloneConfiguration configuration =
                new RedisStandaloneConfiguration(
                        REDIS.getHost(),
                        REDIS.getMappedPort(6379)
                );
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        cache = new RedisNotificationUnreadCountCache(
                redisTemplate,
                new NotificationCacheProperties(Duration.ofSeconds(30))
        );
    }

    @BeforeEach
    void clearRedis() {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    @AfterAll
    static void closeClient() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void shouldStoreCountWithTtlAndEvictIt() {
        cache.put("u001", 7);

        OptionalLong cached = cache.get("u001");
        Long ttlMillis = redisTemplate.getExpire(KEY, TimeUnit.MILLISECONDS);

        assertTrue(cached.isPresent());
        assertEquals(7, cached.orElseThrow());
        assertTrue(ttlMillis != null && ttlMillis > 0 && ttlMillis <= 30_000);

        cache.evict("u001");
        assertFalse(cache.get("u001").isPresent());
    }

    @Test
    void shouldDeleteMalformedCachedValueAndReturnMiss() {
        redisTemplate.opsForValue().set(KEY, "not-a-number");

        assertFalse(cache.get("u001").isPresent());
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(KEY)));
    }
}
