package com.teamflow.core.common.version;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 乐观锁版本工具测试。 */
class VersionsTest {

    @Test
    void shouldValidateAndAdvanceVersion() {
        assertAll(
                () -> assertEquals(
                        0,
                        Versions.requireNonNegative(0, "任务版本号")
                ),
                () -> assertEquals(1, Versions.next(0, "任务版本号")),
                () -> assertEquals(42, Versions.next(41, "任务版本号"))
        );
    }

    @Test
    void shouldRejectNegativeOrOverflowingVersion() {
        IllegalStateException overflow = assertThrows(
                IllegalStateException.class,
                () -> Versions.next(Integer.MAX_VALUE, "任务版本号")
        );

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> Versions.requireNonNegative(
                                -1,
                                "任务版本号"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> Versions.next(-1, "任务版本号")
                ),
                () -> assertInstanceOf(
                        ArithmeticException.class,
                        overflow.getCause()
                )
        );
    }
}
