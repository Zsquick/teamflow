package com.teamflow.core.common.time;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** UTC 时间文本工具测试。 */
class UtcTimeTextTest {

    @Test
    void shouldFormatWithExactlyThreeMillisecondDigits() {
        assertEquals(
                "2026-09-07T10:20:30.123Z",
                UtcTimeText.format(
                        Instant.parse("2026-09-07T10:20:30.123456Z")
                )
        );
    }

    @Test
    void shouldKeepFormattedTimeWithinFixedDatabaseColumnLength() {
        assertAll(
                () -> assertEquals(
                        "9999-12-31T23:59:59.999Z",
                        UtcTimeText.format(
                                Instant.parse("9999-12-31T23:59:59.999Z")
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> UtcTimeText.format(
                                Instant.parse("+10000-01-01T00:00:00Z")
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> UtcTimeText.format(
                                Instant.parse("-0001-12-31T23:59:59.999Z")
                        )
                )
        );
    }

    @Test
    void shouldReturnValidTimeUnchanged() {
        String time = "2026-09-07T10:20:30.123Z";

        assertEquals(time, UtcTimeText.requireValid(time, "创建时间"));
    }

    @Test
    void shouldRejectNonCanonicalOrInvalidTime() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> UtcTimeText.requireValid(
                                "2026-09-07T10:20:30Z",
                                "创建时间"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> UtcTimeText.requireValid(
                                "2026-99-99T10:20:30.123Z",
                                "创建时间"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> UtcTimeText.requireValid(
                                "+10000-01-01T00:00:00.000Z",
                                "创建时间"
                        )
                )
        );
    }

    @Test
    void shouldRequireTimeAtOrAfterLowerBound() {
        String lowerBound = "2026-09-07T10:20:30.123Z";
        String later = "2026-09-07T10:20:30.124Z";

        assertAll(
                () -> assertEquals(
                        lowerBound,
                        UtcTimeText.requireAtOrAfter(
                                lowerBound,
                                lowerBound,
                                "修改时间",
                                "创建时间"
                        )
                ),
                () -> assertEquals(
                        later,
                        UtcTimeText.requireAtOrAfter(
                                later,
                                lowerBound,
                                "修改时间",
                                "创建时间"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> UtcTimeText.requireAtOrAfter(
                                "2026-09-07T10:20:30.122Z",
                                lowerBound,
                                "修改时间",
                                "创建时间"
                        )
                )
        );
    }

    @Test
    void shouldUseInjectedClockForCurrentTime() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-09-07T10:20:30.123Z"),
                ZoneOffset.UTC
        );

        assertEquals(
                "2026-09-07T10:20:30.123Z",
                UtcTimeText.now(clock)
        );
    }
}
