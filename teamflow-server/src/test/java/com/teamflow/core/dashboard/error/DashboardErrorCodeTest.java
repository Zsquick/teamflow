package com.teamflow.core.dashboard.error;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 仪表盘过载与查询失败的稳定错误协议测试。 */
class DashboardErrorCodeTest {

    @Test
    void shouldExposeStableMappings() {
        assertMapping(
                DashboardErrorCode.STATISTICS_TIMEOUT,
                "DASHBOARD_0001",
                "仪表盘统计查询超时，请稍后重试",
                504
        );
        assertMapping(
                DashboardErrorCode.EXECUTOR_BUSY,
                "DASHBOARD_0002",
                "仪表盘请求过多，请稍后重试",
                503
        );
        assertMapping(
                DashboardErrorCode.STATISTICS_UNAVAILABLE,
                "DASHBOARD_0003",
                "仪表盘统计暂时不可用，请稍后重试",
                503
        );
    }

    @Test
    void shouldKeepEveryDashboardCodeUniqueAndNamespaced() {
        Set<String> codes = Arrays.stream(DashboardErrorCode.values())
                .map(DashboardErrorCode::code)
                .collect(Collectors.toSet());

        assertEquals(DashboardErrorCode.values().length, codes.size());
        assertTrue(
                codes.stream().allMatch(code -> code.startsWith("DASHBOARD_"))
        );
    }

    private static void assertMapping(
            DashboardErrorCode errorCode,
            String code,
            String message,
            int httpStatus
    ) {
        assertEquals(code, errorCode.code());
        assertEquals(message, errorCode.message());
        assertEquals(httpStatus, errorCode.httpStatus());
    }
}
