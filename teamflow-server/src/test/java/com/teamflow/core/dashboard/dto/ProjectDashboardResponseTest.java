package com.teamflow.core.dashboard.dto;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 仪表盘快照的数值边界和不可变性测试。 */
class ProjectDashboardResponseTest {

    private static final String NOW = "2026-09-18T03:04:05.678Z";

    @Test
    void shouldDefensivelyCopyOrderedCountMaps() {
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        statusCounts.put("TODO", 2L);
        statusCounts.put("DONE", 1L);
        Map<String, Long> memberCounts = new LinkedHashMap<>();
        memberCounts.put("u001", 1L);

        ProjectDashboardResponse response = new ProjectDashboardResponse(
                "p001",
                3,
                1,
                statusCounts,
                memberCounts,
                NOW
        );
        statusCounts.put("IN_PROGRESS", 9L);
        memberCounts.clear();

        assertAll(
                () -> assertEquals(
                        Map.of("TODO", 2L, "DONE", 1L),
                        response.statusCounts()
                ),
                () -> assertEquals(
                        Map.of("u001", 1L),
                        response.memberCompletedCounts()
                ),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> response.statusCounts().put("TODO", 3L)
                ),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> response.memberCompletedCounts().clear()
                )
        );
    }

    @Test
    void shouldRejectInvalidSnapshotValues() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> response(" ", 0, 0, Map.of(), Map.of(), NOW)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> response("p001", -1, 0, Map.of(), Map.of(), NOW)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> response("p001", 0, -1, Map.of(), Map.of(), NOW)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> response(
                                "p001", 0, 0,
                                Map.of("TODO", -1L), Map.of(), NOW
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> response(
                                "p001", 0, 0,
                                Map.of(), Map.of(), "2026-09-18"
                        )
                )
        );
    }

    private static ProjectDashboardResponse response(
            String projectId,
            long totalTasks,
            long overdueTasks,
            Map<String, Long> statusCounts,
            Map<String, Long> memberCounts,
            String generatedAt
    ) {
        return new ProjectDashboardResponse(
                projectId,
                totalTasks,
                overdueTasks,
                statusCounts,
                memberCounts,
                generatedAt
        );
    }
}
