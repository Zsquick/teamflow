package com.teamflow.scheduling;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

/** 定时扫描、分布式租约和临时文件清理参数。 */
@ConfigurationProperties("teamflow.operations")
public record OperationsProperties(
        Duration dueReminderWindow,
        int dueBatchSize,
        Duration dueLockTtl,
        Duration temporaryFileRetention,
        int temporaryFileMaxDepth
) {

    public OperationsProperties {
        requirePositive(dueReminderWindow, "到期提醒窗口");
        requirePositive(dueLockTtl, "到期扫描锁租期");
        requirePositive(temporaryFileRetention, "临时文件保留时间");
        if (dueBatchSize < 1 || dueBatchSize > 1000) {
            throw new IllegalArgumentException(
                    "到期任务单批数量必须在 1 到 1000 之间"
            );
        }
        if (temporaryFileMaxDepth < 1 || temporaryFileMaxDepth > 20) {
            throw new IllegalArgumentException(
                    "临时文件扫描深度必须在 1 到 20 之间"
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
