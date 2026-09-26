package com.teamflow.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * 基于 Redis 的单设备刷新令牌存储。
 *
 * <p>Redis 只保存刷新令牌的 SHA-256 摘要和过期时间。每个用户只有一个 Key，
 * 因而保存新令牌会使旧令牌立即失效。</p>
 */
@Repository
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "teamflow:refresh:";
    private static final String DIGEST_ALGORITHM = "SHA-256";

    private static final RedisScript<Long> COMPARE_AND_DELETE_SCRIPT =
            RedisScript.of("""
                    local current = redis.call('GET', KEYS[1])
                    if current == ARGV[1] then
                        return redis.call('DEL', KEYS[1])
                    end
                    return 0
                    """, Long.class);

    private static final RedisScript<Long> COMPARE_AND_ROTATE_SCRIPT =
            RedisScript.of("""
                    local current = redis.call('GET', KEYS[1])
                    if current == ARGV[1] then
                        redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
                        return 1
                    end
                    return 0
                    """, Long.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * 创建刷新令牌存储。
     *
     * @param redisTemplate Redis 字符串操作模板
     */
    public RedisRefreshTokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(
                redisTemplate,
                "Redis 字符串模板不能为 null"
        );
    }

    /** {@inheritDoc} */
    @Override
    public void save(String userId, String token, Duration ttl) {
        Objects.requireNonNull(ttl, "刷新令牌有效期不能为 null");

        redisTemplate.opsForValue().set(
                key(userId),
                tokenDigestHex(token),
                ttl
        );
    }

    /** {@inheritDoc} */
    @Override
    public boolean rotate(
            String userId,
            String currentToken,
            String newToken,
            Duration ttl
    ) {
        Objects.requireNonNull(ttl, "刷新令牌有效期不能为 null");

        Long result = redisTemplate.execute(
                COMPARE_AND_ROTATE_SCRIPT,
                List.of(key(userId)),
                tokenDigestHex(currentToken),
                tokenDigestHex(newToken),
                Long.toString(ttl.toMillis())
        );

        return Long.valueOf(1L).equals(result);
    }

    /** {@inheritDoc} */
    @Override
    public void delete(String userId, String token) {
        redisTemplate.execute(
                COMPARE_AND_DELETE_SCRIPT,
                List.of(key(userId)),
                tokenDigestHex(token)
        );
    }

    private static String key(String userId) {
        Objects.requireNonNull(userId, "用户编号不能为 null");
        if (userId.isBlank()) {
            throw new IllegalArgumentException("用户编号不能为空");
        }
        return KEY_PREFIX + userId;
    }

    private static byte[] tokenDigest(String token) {
        Objects.requireNonNull(token, "刷新令牌不能为 null");

        try {
            MessageDigest messageDigest = MessageDigest.getInstance(
                    DIGEST_ALGORITHM
            );
            return messageDigest.digest(
                    token.getBytes(StandardCharsets.UTF_8)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "当前 JVM 不支持 SHA-256",
                    exception
            );
        }
    }

    private static String tokenDigestHex(String token) {
        byte[] digest = tokenDigest(token);
        try {
            return HexFormat.of().formatHex(digest);
        } finally {
            java.util.Arrays.fill(digest, (byte) 0);
        }
    }
}
