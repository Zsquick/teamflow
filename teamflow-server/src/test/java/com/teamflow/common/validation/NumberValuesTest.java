package com.teamflow.common.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 通用数值边界工具测试。 */
class NumberValuesTest {

    @Test
    void shouldAcceptZeroAndPositiveNumbers() {
        assertAll(
                () -> assertEquals(
                        0L,
                        NumberValues.requireNonNegative(0L, "数量")
                ),
                () -> assertEquals(
                        12L,
                        NumberValues.requireNonNegative(12L, "数量")
                )
        );
    }

    @Test
    void shouldRejectNegativeNumbers() {
        assertThrows(
                IllegalArgumentException.class,
                () -> NumberValues.requireNonNegative(-1L, "数量")
        );
    }
}
