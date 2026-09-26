package com.teamflow.common.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 通用文本值工具测试。 */
class TextValuesTest {

    @Test
    void shouldReturnNonBlankTextWithoutChangingIt() {
        assertEquals(
                "  teamflow  ",
                TextValues.requireNonBlank("  teamflow  ", "名称")
        );
    }

    @Test
    void shouldRejectNullAndUnicodeBlankText() {
        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> TextValues.requireNonBlank(null, "名称")
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> TextValues.requireNonBlank(" \t\n", "名称")
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> TextValues.requireNonBlank("\u3000", "名称")
                )
        );
    }

    @Test
    void shouldAllowNullButRejectBlankForOptionalText() {
        assertAll(
                () -> assertNull(
                        TextValues.requireOptionalNonBlank(null, "负责人")
                ),
                () -> assertEquals(
                        "u001",
                        TextValues.requireOptionalNonBlank(
                                "u001",
                                "负责人"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> TextValues.requireOptionalNonBlank(
                                " ",
                                "负责人"
                        )
                )
        );
    }

    @Test
    void shouldStripOptionalTextAndConvertBlankToNull() {
        assertAll(
                () -> assertNull(TextValues.stripToNull(null)),
                () -> assertNull(TextValues.stripToNull(" \t\n")),
                () -> assertEquals(
                        "团队描述",
                        TextValues.stripToNull("  团队描述  ")
                )
        );
    }
}
