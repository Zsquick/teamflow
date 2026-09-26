package com.teamflow.sse;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

/** SSE 连接超时和心跳参数。 */
@ConfigurationProperties("teamflow.sse")
public record SseProperties(
        Duration timeout,
        Duration heartbeatInterval
) {

    public SseProperties {
        timeout = requirePositive(timeout, "SSE 连接超时");
        heartbeatInterval = requirePositive(
                heartbeatInterval,
                "SSE 心跳间隔"
        );
        if (heartbeatInterval.compareTo(timeout) >= 0) {
            throw new IllegalArgumentException(
                    "SSE 心跳间隔必须小于连接超时"
            );
        }
        try {
            timeout.toMillis();
            heartbeatInterval.toMillis();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "SSE 时间配置超出毫秒范围",
                    exception
            );
        }
    }

    private static Duration requirePositive(
            Duration value,
            String fieldName
    ) {
        Duration duration = Objects.requireNonNull(
                value,
                fieldName + "不能为 null"
        );
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(fieldName + "必须大于 0");
        }
        return duration;
    }
}
