package com.teamflow.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

/** 通知缓存参数。 */
@ConfigurationProperties("teamflow.notification-cache")
public record NotificationCacheProperties(Duration unreadCountTtl) {

    public NotificationCacheProperties {
        Objects.requireNonNull(
                unreadCountTtl,
                "通知未读数缓存时间不能为 null"
        );
        if (unreadCountTtl.isZero() || unreadCountTtl.isNegative()) {
            throw new IllegalArgumentException(
                    "通知未读数缓存时间必须大于 0"
            );
        }
    }
}
