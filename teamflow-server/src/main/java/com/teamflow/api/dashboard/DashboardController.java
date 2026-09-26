package com.teamflow.api.dashboard;

import com.teamflow.common.api.ApiResponse;
import com.teamflow.core.dashboard.dto.ProjectDashboardResponse;
import com.teamflow.core.dashboard.service.DashboardService;
import com.teamflow.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/** 登录用户可访问的项目仪表盘接口。 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = Objects.requireNonNull(
                dashboardService,
                "仪表盘服务不能为 null"
        );
    }

    @GetMapping("/projects/{projectId}")
    public ApiResponse<ProjectDashboardResponse> project(@AuthenticationPrincipal AuthenticatedUser user,
                                                         @PathVariable String projectId) {
        return ApiResponse.success(
                dashboardService.getProjectDashboard(user.id(), projectId)
        );
    }
}
