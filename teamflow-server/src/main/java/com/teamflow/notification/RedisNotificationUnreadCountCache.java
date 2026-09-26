package com.teamflow.notification;

import com.teamflow.common.validation.NumberValues;
import com.teamflow.common.validation.TextValues;
import com.teamflow.core.notification.service.NotificationUnreadCountCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.OptionalLong;

/** Redis 通知未读数缓存；任何 Redis 故障都按缓存未命中降级。 */
@Repository
public class RedisNotificationUnreadCountCache
        implements NotificationUnreadCountCache {

    private static final Logger log = LoggerFactory.getLogger(
            RedisNotificationUnreadCountCache.class
    );
    private static final String KEY_PREFIX =
            "teamflow:notification:unread:";

    private final StringRedisTemplate redisTemplate;
    private final NotificationCacheProperties properties;

    public RedisNotificationUnreadCountCache(
            StringRedisTemplate redisTemplate,
            NotificationCacheProperties properties
    ) {
        this.redisTemplate = Objects.requireNonNull(
                redisTemplate,
                "Redis 字符串模板不能为 null"
        );
        this.properties = Objects.requireNonNull(
                properties,
                "通知缓存配置不能为 null"
        );
    }

    @Override
    public OptionalLong get(String userId) {
        String key = key(userId);
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return OptionalLong.empty();
            }
            final long parsed;
            try {
                parsed = Long.parseLong(value);
            } catch (NumberFormatException invalidValue) {
                redisTemplate.delete(key);
                log.warn("已删除格式错误的 Redis 通知未读数缓存");
                return OptionalLong.empty();
            }
            if (parsed < 0L) {
                redisTemplate.delete(key);
                return OptionalLong.empty();
            }
            return OptionalLong.of(parsed);
        } catch (RuntimeException exception) {
            log.warn(
                    "读取 Redis 通知未读数失败 failureType={}",
                    exception.getClass().getName()
            );
            return OptionalLong.empty();
        }
    }

    @Override
    public void put(String userId, long unreadCount) {
        NumberValues.requireNonNegative(unreadCount, "通知未读数量");
        try {
            redisTemplate.opsForValue().set(
                    key(userId),
                    Long.toString(unreadCount),
                    properties.unreadCountTtl()
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "写入 Redis 通知未读数失败 failureType={}",
                    exception.getClass().getName()
            );
        }
    }

    @Override
    public void evict(String userId) {
        try {
            redisTemplate.delete(key(userId));
        } catch (RuntimeException exception) {
            log.warn(
                    "删除 Redis 通知未读数失败 failureType={}",
                    exception.getClass().getName()
            );
        }
    }

    private static String key(String userId) {
        return KEY_PREFIX + TextValues.requireNonBlank(
                userId,
                "通知缓存用户编号"
        );
    }
}
