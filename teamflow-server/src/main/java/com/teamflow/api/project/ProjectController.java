package com.teamflow.api.project;

import com.teamflow.audit.Audited;
import com.teamflow.common.api.ApiResponse;
import com.teamflow.core.project.dto.CreateProjectRequest;
import com.teamflow.core.project.dto.ProjectResponse;
import com.teamflow.core.project.dto.UpdateProjectRequest;
import com.teamflow.core.project.service.ProjectService;
import com.teamflow.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/** 登录用户可访问的项目管理 REST 接口。 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = Objects.requireNonNull(
                projectService,
                "项目服务不能为 null"
        );
    }

    @Audited(
            action = "PROJECT_CREATE",
            resourceType = "TEAM",
            resourceId = "#request.teamId"
    )
    @PostMapping
    public ApiResponse<ProjectResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                               @Valid @RequestBody CreateProjectRequest request) {
        return ApiResponse.success(projectService.create(user.id(), request));
    }

    @GetMapping
    public ApiResponse<List<ProjectResponse>> listByTeam(@AuthenticationPrincipal AuthenticatedUser user,
                                                         @RequestParam String teamId) {
        return ApiResponse.success(
                projectService.listByTeam(user.id(), teamId)
        );
    }

    @GetMapping("/{projectId}")
    public ApiResponse<ProjectResponse> get(@AuthenticationPrincipal AuthenticatedUser user,
                                            @PathVariable String projectId) {
        return ApiResponse.success(projectService.get(user.id(), projectId));
    }

    @Audited(
            action = "PROJECT_UPDATE",
            resourceType = "PROJECT",
            resourceId = "#projectId"
    )
    @PutMapping("/{projectId}")
    public ApiResponse<ProjectResponse> update(@AuthenticationPrincipal AuthenticatedUser user,
                                               @PathVariable String projectId,
                                               @Valid @RequestBody UpdateProjectRequest request) {
        return ApiResponse.success(
                projectService.update(user.id(), projectId, request)
        );
    }
}
