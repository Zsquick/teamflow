package com.teamflow.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** 使用 Redis Lua 原子计数实现多实例共享的固定窗口限流。 */
@Component
public class RequestRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(
            RequestRateLimiter.class
    );
    private static final String KEY_PREFIX = "teamflow:rate-limit:";
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT =
            new DefaultRedisScript<>(
                    "local current = redis.call('incr', KEYS[1]); "
                            + "if current == 1 then "
                            + "redis.call('pexpire', KEYS[1], ARGV[1]); "
                            + "end; return current;",
                    Long.class
            );

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    public RequestRateLimiter(StringRedisTemplate redisTemplate, Clock clock) {
        this.redisTemplate = Objects.requireNonNull(
                redisTemplate,
                "Redis 模板不能为 null"
        );
        this.clock = Objects.requireNonNull(clock, "限流时钟不能为 null");
    }

    /**
     * 消耗一个窗口配额。Redis 暂时不可用时记录告警并放行，避免缓存故障
     * 把所有 HTTP 入口一并中断。
     */
    public void check(
            String scope,
            String subject,
            RateLimitProperties.Policy policy
    ) {
        String validScope = requireToken(scope, "限流范围");
        String validSubject = requireToken(subject, "限流主体");
        Objects.requireNonNull(policy, "限流策略不能为 null");

        long windowMillis = policy.window().toMillis();
        long nowMillis = clock.millis();
        long windowNumber = Math.floorDiv(nowMillis, windowMillis);
        long expiresInMillis = Math.multiplyExact(windowMillis, 2L);
        String key = KEY_PREFIX
                + validScope
                + ":"
                + sha256(validSubject)
                + ":"
                + windowNumber;

        final Long current;
        try {
            current = redisTemplate.execute(
                    INCREMENT_SCRIPT,
                    List.of(key),
                    Long.toString(expiresInMillis)
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "Redis 限流暂时不可用，当前请求降级放行 scope={} failureType={}",
                    validScope,
                    exception.getClass().getName()
            );
            return;
        }
        if (current == null) {
            log.warn("Redis 限流脚本没有返回计数，当前请求降级放行 scope={}", validScope);
            return;
        }
        if (current > policy.maxRequests()) {
            long remainingMillis = windowMillis
                    - Math.floorMod(nowMillis, windowMillis);
            long retryAfterSeconds = Math.max(
                    1L,
                    Math.ceilDiv(remainingMillis, 1_000L)
            );
            throw new RateLimitExceededException(retryAfterSeconds);
        }
    }

    private static String requireToken(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + "不能为 null");
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return normalized;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 Java 运行环境不支持 SHA-256", exception);
        }
    }
}
