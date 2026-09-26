package com.teamflow.scheduling;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 运维参数边界测试。 */
class OperationsPropertiesTest {

    @Test
    void shouldAcceptPositiveBoundedValues() {
        assertDoesNotThrow(() -> properties(200, 4));
    }

    @Test
    void shouldRejectInvalidDurationsAndBounds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationsProperties(
                        Duration.ZERO,
                        200,
                        Duration.ofMinutes(4),
                        Duration.ofHours(24),
                        4
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> properties(0, 4)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> properties(1001, 4)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> properties(200, 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> properties(200, 21)
        );
    }

    private OperationsProperties properties(int batchSize, int maxDepth) {
        return new OperationsProperties(
                Duration.ofHours(24),
                batchSize,
                Duration.ofMinutes(4),
                Duration.ofHours(24),
                maxDepth
        );
    }
}
