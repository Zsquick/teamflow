package com.teamflow.api.dashboard;

import com.teamflow.common.api.ApiResponse;
import com.teamflow.core.dashboard.dto.ProjectDashboardResponse;
import com.teamflow.core.dashboard.service.DashboardService;
import com.teamflow.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 仪表盘 HTTP 接口的认证身份委托与统一响应测试。 */
@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    private static final String USER_ID = "u001";
    private static final String PROJECT_ID = "p001";
    private static final String NOW = "2026-09-18T03:04:05.678Z";

    @Mock
    private DashboardService dashboardService;

    private DashboardController controller;
    private AuthenticatedUser currentUser;

    @BeforeEach
    void setUp() {
        controller = new DashboardController(dashboardService);
        currentUser = new AuthenticatedUser(
                USER_ID,
                "zhou",
                Set.of("ROLE_USER")
        );
    }

    @Test
    void shouldRequireDashboardService() {
        assertThrows(
                NullPointerException.class,
                () -> new DashboardController(null)
        );
    }

    @Test
    void shouldReturnProjectDashboardForAuthenticatedUser() {
        ProjectDashboardResponse expected = new ProjectDashboardResponse(
                PROJECT_ID,
                3,
                1,
                Map.of("TODO", 2L, "IN_PROGRESS", 0L, "DONE", 1L),
                Map.of("u002", 1L),
                NOW
        );
        when(dashboardService.getProjectDashboard(USER_ID, PROJECT_ID))
                .thenReturn(expected);

        ApiResponse<ProjectDashboardResponse> response = controller.project(
                currentUser,
                PROJECT_ID
        );

        assertAll(
                () -> assertEquals("COMMON_0000", response.code()),
                () -> assertEquals("成功", response.message()),
                () -> assertSame(expected, response.data())
        );
        verify(dashboardService).getProjectDashboard(USER_ID, PROJECT_ID);
    }

    private static void assertSame(Object expected, Object actual) {
        org.junit.jupiter.api.Assertions.assertSame(expected, actual);
    }
}
