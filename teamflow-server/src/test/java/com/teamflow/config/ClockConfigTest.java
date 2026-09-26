package com.teamflow.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 项目时钟配置测试。 */
class ClockConfigTest {

    @Test
    void shouldProvideUtcClock() {
        Clock clock = new ClockConfig().clock();

        assertEquals(ZoneOffset.UTC, clock.getZone());
    }
}
