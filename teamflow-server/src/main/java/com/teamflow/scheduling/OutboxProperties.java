package com.teamflow.scheduling;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

/** Outbox 批次、领取租约、确认与退避参数。 */
@ConfigurationProperties("teamflow.outbox")
public record OutboxProperties(
        int batchSize,
        int maxAttempts,
        Duration claimTtl,
        Duration confirmTimeout,
        Duration retryInitialDelay,
        Duration retryMaxDelay
) {

    public OutboxProperties {
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException(
                    "Outbox 单批数量必须在 1 到 1000 之间"
            );
        }
        if (maxAttempts < 1 || maxAttempts > 100) {
            throw new IllegalArgumentException(
                    "Outbox 最大尝试次数必须在 1 到 100 之间"
            );
        }
        requirePositive(claimTtl, "Outbox 领取租期");
        requirePositive(confirmTimeout, "Outbox 发布确认超时");
        requirePositive(retryInitialDelay, "Outbox 初始重试间隔");
        requirePositive(retryMaxDelay, "Outbox 最大重试间隔");
        if (claimTtl.compareTo(confirmTimeout) <= 0) {
            throw new IllegalArgumentException(
                    "Outbox 领取租期必须大于发布确认超时"
            );
        }
        if (retryMaxDelay.compareTo(retryInitialDelay) < 0) {
            throw new IllegalArgumentException(
                    "Outbox 最大重试间隔不能小于初始间隔"
            );
        }
    }

    private static void requirePositive(Duration value, String fieldName) {
        Objects.requireNonNull(value, fieldName + "不能为 null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(fieldName + "必须大于 0");
        }
    }
}
