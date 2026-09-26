package com.teamflow.api.project;

import com.teamflow.common.api.ApiResponse;
import com.teamflow.core.project.domain.ProjectStatus;
import com.teamflow.core.project.dto.CreateProjectRequest;
import com.teamflow.core.project.dto.ProjectResponse;
import com.teamflow.core.project.dto.UpdateProjectRequest;
import com.teamflow.core.project.service.ProjectService;
import com.teamflow.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 项目 HTTP 接口的认证身份委托、字符串编号与统一响应测试。 */
@ExtendWith(MockitoExtension.class)
class ProjectControllerTest {

    private static final String NOW = "2026-09-15T03:04:05.678Z";
    private static final String PROJECT_ID = "p001";
    private static final String TEAM_ID = "tm001";
    private static final String CURRENT_USER_ID = "u001";

    @Mock
    private ProjectService projectService;

    private ProjectController controller;
    private AuthenticatedUser currentUser;

    @BeforeEach
    void setUp() {
        controller = new ProjectController(projectService);
        currentUser = new AuthenticatedUser(
                CURRENT_USER_ID,
                "zhou",
                Set.of("ROLE_USER")
        );
    }

    @Test
    void shouldRequireProjectService() {
        assertThrows(
                NullPointerException.class,
                () -> new ProjectController(null)
        );
    }

    @Test
    void shouldCreateProjectForAuthenticatedUser() {
        CreateProjectRequest request = new CreateProjectRequest(
                TEAM_ID,
                "TeamFlow",
                "TF",
                "Collaboration project"
        );
        ProjectResponse expected = projectResponse(
                "TeamFlow",
                "Collaboration project",
                ProjectStatus.ACTIVE,
                0
        );
        when(projectService.create(CURRENT_USER_ID, request))
                .thenReturn(expected);

        ApiResponse<ProjectResponse> response = controller.create(
                currentUser,
                request
        );

        assertSuccessfulResponse(response, expected);
        verify(projectService).create(CURRENT_USER_ID, request);
    }

    @Test
    void shouldListTeamProjectsWithAuthenticatedUserAndStringTeamId() {
        List<ProjectResponse> expected = List.of(
                projectResponse(
                        "TeamFlow",
                        null,
                        ProjectStatus.ACTIVE,
                        0
                )
        );
        when(projectService.listByTeam(CURRENT_USER_ID, TEAM_ID))
                .thenReturn(expected);

        ApiResponse<List<ProjectResponse>> response = controller.listByTeam(
                currentUser,
                TEAM_ID
        );

        assertSuccessfulResponse(response, expected);
        verify(projectService).listByTeam(CURRENT_USER_ID, TEAM_ID);
    }

    @Test
    void shouldGetStringProjectIdForAuthenticatedUser() {
        ProjectResponse expected = projectResponse(
                "TeamFlow",
                null,
                ProjectStatus.ACTIVE,
                2
        );
        when(projectService.get(CURRENT_USER_ID, PROJECT_ID))
                .thenReturn(expected);

        ApiResponse<ProjectResponse> response = controller.get(
                currentUser,
                PROJECT_ID
        );

        assertSuccessfulResponse(response, expected);
        verify(projectService).get(CURRENT_USER_ID, PROJECT_ID);
    }

    @Test
    void shouldUpdateStringProjectIdWithExpectedVersion() {
        UpdateProjectRequest request = new UpdateProjectRequest(
                "Archived TeamFlow",
                "Completed collaboration project",
                ProjectStatus.ARCHIVED,
                2
        );
        ProjectResponse expected = projectResponse(
                "Archived TeamFlow",
                "Completed collaboration project",
                ProjectStatus.ARCHIVED,
                3
        );
        when(projectService.update(
                CURRENT_USER_ID,
                PROJECT_ID,
                request
        )).thenReturn(expected);

        ApiResponse<ProjectResponse> response = controller.update(
                currentUser,
                PROJECT_ID,
                request
        );

        assertSuccessfulResponse(response, expected);
        verify(projectService).update(
                CURRENT_USER_ID,
                PROJECT_ID,
                request
        );
    }

    private <T> void assertSuccessfulResponse(
            ApiResponse<T> response,
            T expectedData
    ) {
        assertAll(
                () -> assertEquals("COMMON_0000", response.code()),
                () -> assertEquals("成功", response.message()),
                () -> assertEquals(expectedData, response.data())
        );
    }

    private ProjectResponse projectResponse(
            String name,
            String description,
            ProjectStatus status,
            int version
    ) {
        return new ProjectResponse(
                PROJECT_ID,
                TEAM_ID,
                name,
                "TF",
                description,
                status,
                CURRENT_USER_ID,
                version,
                NOW,
                NOW
        );
    }
}
