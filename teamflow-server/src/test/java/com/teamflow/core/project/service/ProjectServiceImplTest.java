package com.teamflow.core.project.service;

import com.teamflow.common.error.BusinessException;
import com.teamflow.core.common.id.ReadableIdGenerator;
import com.teamflow.core.common.id.ResourceType;
import com.teamflow.core.project.domain.Project;
import com.teamflow.core.project.domain.ProjectStatus;
import com.teamflow.core.project.dto.CreateProjectRequest;
import com.teamflow.core.project.dto.ProjectResponse;
import com.teamflow.core.project.dto.UpdateProjectRequest;
import com.teamflow.core.project.error.ProjectErrorCode;
import com.teamflow.core.project.mapper.ProjectMapper;
import com.teamflow.core.project.service.impl.ProjectServiceImpl;
import com.teamflow.core.team.domain.TeamMember;
import com.teamflow.core.team.domain.TeamRole;
import com.teamflow.core.team.error.TeamErrorCode;
import com.teamflow.core.team.service.TeamAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 项目创建、查询、权限边界和乐观锁业务测试。 */
@ExtendWith(MockitoExtension.class)
class ProjectServiceImplTest {

    private static final String CREATED_AT = "2026-09-14T01:02:03.456Z";
    private static final String PREVIOUS_UPDATED_AT =
            "2026-09-14T02:03:04.567Z";
    private static final String NOW = "2026-09-15T03:04:05.678Z";
    private static final String PROJECT_ID = "p001";
    private static final String TEAM_ID = "tm001";
    private static final String CURRENT_USER_ID = "u001";

    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private TeamAuthorizationService authorizationService;
    @Mock
    private ReadableIdGenerator idGenerator;

    private ProjectServiceImpl projectService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse(NOW), ZoneOffset.UTC);
        projectService = new ProjectServiceImpl(
                projectMapper,
                authorizationService,
                idGenerator,
                clock
        );
    }

    @Test
    void shouldRequireEveryDependency() {
        Clock clock = Clock.fixed(Instant.parse(NOW), ZoneOffset.UTC);

        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new ProjectServiceImpl(
                                null,
                                authorizationService,
                                idGenerator,
                                clock
                        )
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new ProjectServiceImpl(
                                projectMapper,
                                null,
                                idGenerator,
                                clock
                        )
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new ProjectServiceImpl(
                                projectMapper,
                                authorizationService,
                                null,
                                clock
                        )
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new ProjectServiceImpl(
                                projectMapper,
                                authorizationService,
                                idGenerator,
                                null
                        )
                )
        );
    }

    @Test
    void shouldCreateNormalizedProjectAfterAdministratorAuthorization() {
        CreateProjectRequest request = new CreateProjectRequest(
                TEAM_ID,
                " TeamFlow API ",
                "tf-api",
                " Core collaboration service "
        );
        when(authorizationService.requireAtLeast(
                TEAM_ID,
                CURRENT_USER_ID,
                TeamRole.ADMIN
        )).thenReturn(TeamRole.ADMIN);
        when(idGenerator.nextId(ResourceType.PROJECT)).thenReturn(PROJECT_ID);
        when(projectMapper.countByTeamIdAndProjectKey(TEAM_ID, "TF-API"))
                .thenReturn(0);
        when(projectMapper.insert(any(Project.class))).thenReturn(1);

        ProjectResponse response = projectService.create(
                CURRENT_USER_ID,
                request
        );

        ArgumentCaptor<Project> projectCaptor =
                ArgumentCaptor.forClass(Project.class);
        InOrder order = inOrder(
                authorizationService,
                idGenerator,
                projectMapper
        );
        order.verify(authorizationService).requireAtLeast(
                TEAM_ID,
                CURRENT_USER_ID,
                TeamRole.ADMIN
        );
        order.verify(idGenerator).nextId(ResourceType.PROJECT);
        order.verify(projectMapper).countByTeamIdAndProjectKey(
                TEAM_ID,
                "TF-API"
        );
        order.verify(projectMapper).insert(projectCaptor.capture());

        Project savedProject = projectCaptor.getValue();
        assertAll(
                () -> assertEquals(PROJECT_ID, savedProject.getId()),
                () -> assertEquals(TEAM_ID, savedProject.getTeamId()),
                () -> assertEquals("TeamFlow API", savedProject.getName()),
                () -> assertEquals("TF-API", savedProject.getProjectKey()),
                () -> assertEquals(
                        "Core collaboration service",
                        savedProject.getDescription()
                ),
                () -> assertEquals(
                        ProjectStatus.ACTIVE,
                        savedProject.getStatus()
                ),
                () -> assertEquals(
                        CURRENT_USER_ID,
                        savedProject.getCreatedBy()
                ),
                () -> assertEquals(0, savedProject.getVersion()),
                () -> assertEquals(NOW, savedProject.getCreatedAt()),
                () -> assertEquals(NOW, savedProject.getUpdatedAt()),
                () -> assertEquals(PROJECT_ID, response.id()),
                () -> assertEquals(TEAM_ID, response.teamId()),
                () -> assertEquals("TF-API", response.projectKey()),
                () -> assertEquals(CURRENT_USER_ID, response.createdBy()),
                () -> assertEquals(ProjectStatus.ACTIVE, response.status()),
                () -> assertEquals(0, response.version()),
                () -> assertEquals(NOW, response.createdAt()),
                () -> assertEquals(NOW, response.updatedAt())
        );
    }

    @Test
    void shouldNormalizeBlankDescriptionToNullWhenCreating() {
        prepareSuccessfulCreate();

        ProjectResponse response = projectService.create(
                CURRENT_USER_ID,
                new CreateProjectRequest(
                        TEAM_ID,
                        "Project",
                        "tf",
                        "   "
                )
        );

        assertNull(response.description());
        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectMapper).insert(captor.capture());
        assertNull(captor.getValue().getDescription());
    }

    @Test
    void shouldStopCreateBeforeIdAndPersistenceWhenPermissionIsRejected() {
        doThrow(new BusinessException(
                TeamErrorCode.INSUFFICIENT_PERMISSION
        )).when(authorizationService).requireAtLeast(
                TEAM_ID,
                CURRENT_USER_ID,
                TeamRole.ADMIN
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> projectService.create(
                        CURRENT_USER_ID,
                        createRequest()
                )
        );

        assertEquals(
                TeamErrorCode.INSUFFICIENT_PERMISSION,
                exception.getErrorCode()
        );
        verifyNoInteractions(idGenerator, projectMapper);
    }

    @Test
    void shouldRejectExistingNormalizedProjectKeyBeforeInsert() {
        when(authorizationService.requireAtLeast(
                TEAM_ID,
                CURRENT_USER_ID,
                TeamRole.ADMIN
        )).thenReturn(TeamRole.OWNER);
        when(idGenerator.nextId(ResourceType.PROJECT)).thenReturn(PROJECT_ID);
        when(projectMapper.countByTeamIdAndProjectKey(TEAM_ID, "TF"))
                .thenReturn(1);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> projectService.create(
                        CURRENT_USER_ID,
                        new CreateProjectRequest(
                                TEAM_ID,
                                "Project",
                                "tf",
                                null
                        )
                )
        );

        assertEquals(
                ProjectErrorCode.PROJECT_KEY_ALREADY_EXISTS,
                exception.getErrorCode()
        );
        verify(projectMapper, never()).insert(any(Project.class));
    }

    @Test
    void shouldConvertConcurrentDuplicateProjectKeyInsert() {
        when(authorizationService.requireAtLeast(
                TEAM_ID,
                CURRENT_USER_ID,
                TeamRole.ADMIN
        )).thenReturn(TeamRole.OWNER);
        when(idGenerator.nextId(ResourceType.PROJECT)).thenReturn(PROJECT_ID);
        when(projectMapper.countByTeamIdAndProjectKey(TEAM_ID, "TF"))
                .thenReturn(0);
        when(projectMapper.insert(any(Project.class))).thenThrow(
                new DuplicateKeyException("concurrent project key")
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> projectService.create(
                        CURRENT_USER_ID,
                        createRequest()
                )
        );

        assertEquals(
                ProjectErrorCode.PROJECT_KEY_ALREADY_EXISTS,
                exception.getErrorCode()
        );
    }

    @Test
    void shouldFailWhenProjectInsertDoesNotAffectExactlyOneRow() {
        when(authorizationService.requireAtLeast(
                TEAM_ID,
                CURRENT_USER_ID,
                TeamRole.ADMIN
        )).thenReturn(TeamRole.ADMIN);
        when(idGenerator.nextId(ResourceType.PROJECT)).thenReturn(PROJECT_ID);
        when(projectMapper.countByTeamIdAndProjectKey(TEAM_ID, "TF"))
                .thenReturn(0);
        when(projectMapper.insert(any(Project.class))).thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> projectService.create(
                        CURRENT_USER_ID,
                        createRequest()
                )
        );
    }

    @Test
    void shouldListProjectsOnlyAfterMembershipAuthorization() {
        Project first = project(
                "p001",
                "Alpha",
                ProjectStatus.ACTIVE,
                0,
                PREVIOUS_UPDATED_AT
        );
        Project second = project(
                "p002",
                "Beta",
                ProjectStatus.ARCHIVED,
                3,
                PREVIOUS_UPDATED_AT
        );
        when(authorizationService.requireMember(TEAM_ID, CURRENT_USER_ID))
                .thenReturn(currentMember());
        when(projectMapper.findByTeamId(TEAM_ID))
                .thenReturn(List.of(first, second));

        List<ProjectResponse> responses = projectService.listByTeam(
                CURRENT_USER_ID,
                TEAM_ID
        );

        assertAll(
                () -> assertEquals(
                        List.of("p001", "p002"),
                        responses.stream().map(ProjectResponse::id).toList()
                ),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> responses.add(ProjectResponse.from(first))
                )
        );
        InOrder order = inOrder(authorizationService, projectMapper);
        order.verify(authorizationService).requireMember(
                TEAM_ID,
                CURRENT_USER_ID
        );
        order.verify(projectMapper).findByTeamId(TEAM_ID);
    }

    @Test
    void shouldNotQueryProjectsWhenTeamMembershipIsRejected() {
        doThrow(new BusinessException(TeamErrorCode.TEAM_NOT_FOUND))
                .when(authorizationService)
                .requireMember(TEAM_ID, CURRENT_USER_ID);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> projectService.listByTeam(CURRENT_USER_ID, TEAM_ID)
        );

        assertEquals(TeamErrorCode.TEAM_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(projectMapper);
    }

    @Test
    void shouldLoadProjectThenAuthorizeItsTeamBeforeReturningDetails() {
        Project project = activeProject(2);
        when(projectMapper.findById(PROJECT_ID))
                .thenReturn(Optional.of(project));
        when(authorizationService.requireMember(TEAM_ID, CURRENT_USER_ID))
                .thenReturn(currentMember());

        ProjectResponse response = projectService.get(
                CURRENT_USER_ID,
                PROJECT_ID
        );

        assertAll(
                () -> assertEquals(PROJECT_ID, response.id()),
                () -> assertEquals(TEAM_ID, response.teamId()),
                () -> assertEquals(2, response.version()),
                () -> assertEquals(CURRENT_USER_ID, response.createdBy())
        );
        InOrder order = inOrder(projectMapper, authorizationService);
        order.verify(projectMapper).findById(PROJECT_ID);
        order.verify(authorizationService).requireMember(
                TEAM_ID,
                CURRENT_USER_ID
        );
    }

    @Test
    void shouldReportMissingProjectWithoutRunningTeamAuthorization() {
        when(projectMapper.findById(PROJECT_ID)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> projectService.get(CURRENT_USER_ID, PROJECT_ID)
        );

        assertEquals(
                ProjectErrorCode.PROJECT_NOT_FOUND,
                exception.getErrorCode()
        );
        verifyNoInteractions(authorizationService);
    }

    @Test
    void shouldHideMissingTeamMembershipAsProjectNotFoundOnGet() {
        when(projectMapper.findById(PROJECT_ID))
                .thenReturn(Optional.of(activeProject(0)));
        doThrow(new BusinessException(TeamErrorCode.TEAM_NOT_FOUND))
                .when(authorizationService)
                .requireMember(TEAM_ID, CURRENT_USER_ID);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> projectService.get(CURRENT_USER_ID, PROJECT_ID)
        );

        assertEquals(
                ProjectErrorCode.PROJECT_NOT_FOUND,
                exception.getErrorCode()
        );
    }

    @Test
    void shouldPreserveNonMissingAuthorizationErrorsOnGet() {
        when(projectMapper.findById(PROJECT_ID))
                .thenReturn(Optional.of(activeProject(0)));
        BusinessException denied = new BusinessException(
                TeamErrorCode.INSUFFICIENT_PERMISSION
        );
        doThrow(denied).when(authorizationService)
                .requireMember(TEAM_ID, CURRENT_USER_ID);

        BusinessException actual = assertThrows(
                BusinessException.class,
                () -> projectService.get(CURRENT_USER_ID, PROJECT_ID)
        );

        assertSame(denied, actual);
    }

    @Test
    void shouldUpdateWithObservedVersionAndReturnReloadedProject() {
        Project current = activeProject(2);
        Project reloaded = new Project(
                PROJECT_ID,
                TEAM_ID,
                "Updated Project",
                "TF",
                "Updated description",
                ProjectStatus.ARCHIVED,
                CURRENT_USER_ID,
                3,
                CREATED_AT,
                NOW
        );
        when(projectMapper.findByIdForUpdate(PROJECT_ID))
                .thenReturn(Optional.of(current));
        when(projectMapper.findById(PROJECT_ID))
                .thenReturn(Optional.of(reloaded));
        when(authorizationService.requireAtLeast(
                TEAM_ID,
                CURRENT_USER_ID,
                TeamRole.ADMIN
        )).thenReturn(TeamRole.ADMIN);
        when(projectMapper.update(any(Project.class), eq(2))).thenReturn(1);

        ProjectResponse response = projectService.update(
                CURRENT_USER_ID,
                PROJECT_ID,
                new UpdateProjectRequest(
                        " Updated Project ",
                        " Updated description ",
                        ProjectStatus.ARCHIVED,
                        2
                )
        );

        ArgumentCaptor<Project> projectCaptor =
                ArgumentCaptor.forClass(Project.class);
        InOrder order = inOrder(projectMapper, authorizationService);
        order.verify(projectMapper).findByIdForUpdate(PROJECT_ID);
        order.verify(authorizationService).requireAtLeast(
                TEAM_ID,
                CURRENT_USER_ID,
                TeamRole.ADMIN
        );
        order.verify(projectMapper).update(projectCaptor.capture(), eq(2));
        order.verify(projectMapper).findById(PROJECT_ID);

        Project saved = projectCaptor.getValue();
        assertAll(
                () -> assertEquals(PROJECT_ID, saved.getId()),
                () -> assertEquals(TEAM_ID, saved.getTeamId()),
                () -> assertEquals("TF", saved.getProjectKey()),
                () -> assertEquals(CURRENT_USER_ID, saved.getCreatedBy()),
                () -> assertEquals("Updated Project", saved.getName()),
                () -> assertEquals(
                        "Updated description",
                        saved.getDescription()
                ),
                () -> assertEquals(ProjectStatus.ARCHIVED, saved.getStatus()),
                () -> assertEquals(3, saved.getVersion()),
                () -> assertEquals(NOW, saved.getUpdatedAt()),
                () -> assertEquals("Updated Project", response.name()),
                () -> assertEquals(ProjectStatus.ARCHIVED, response.status()),
                () -> assertEquals(3, response.version()),
                () -> assertEquals(NOW, response.updatedAt())
        );
    }

    @Test
    void shouldRejectUpdateBeforeMutationWhenAdministratorAuthorizationFails() {
        when(projectMapper.findByIdForUpdate(PROJECT_ID))
                .thenReturn(Optional.of(activeProject(2)));
        doThrow(new BusinessException(
                TeamErrorCode.INSUFFICIENT_PERMISSION
        )).when(authorizationService).requireAtLeast(
                TEAM_ID,
                CURRENT_USER_ID,
                TeamRole.ADMIN
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> projectService.update(
                        CURRENT_USER_ID,
                        PROJECT_ID,
                        updateRequest(2)
                )
        );

        assertEquals(
                TeamErrorCode.INSUFFICIENT_PERMISSION,
                exception.getErrorCode()
        );
        verify(projectMapper, never()).update(any(Project.class), anyInt());
    }

    @Test
    void shouldHideMissingTeamMembershipAsProjectNotFoundOnUpdate() {
        when(projectMapper.findByIdForUpdate(PROJECT_ID))
                .thenReturn(Optional.of(activeProject(2)));
        doThrow(new BusinessException(TeamErrorCode.TEAM_NOT_FOUND))
                .when(authorizationService)
                .requireAtLeast(TEAM_ID, CURRENT_USER_ID, TeamRole.ADMIN);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> projectService.update(
                        CURRENT_USER_ID,
                        PROJECT_ID,
                        updateRequest(2)
                )
        );

        assertEquals(
                ProjectErrorCode.PROJECT_NOT_FOUND,
                exception.getErrorCode()
        );
        verify(projectMapper, never()).update(any(Project.class), anyInt());
    }

    @Test
    void shouldRejectStaleRequestVersionAfterAuthorization() {
        when(projectMapper.findByIdForUpdate(PROJECT_ID))
                .thenReturn(Optional.of(activeProject(2)));
        when(authorizationService.requireAtLeast(
                TEAM_ID,
                CURRENT_USER_ID,
                TeamRole.ADMIN
        )).thenReturn(TeamRole.ADMIN);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> projectService.update(
                        CURRENT_USER_ID,
                        PROJECT_ID,
                        updateRequest(1)
                )
        );

        assertEquals(
                ProjectErrorCode.PROJECT_VERSION_CONFLICT,
                exception.getErrorCode()
        );
        verify(authorizationService).requireAtLeast(
                TEAM_ID,
                CURRENT_USER_ID,
                TeamRole.ADMIN
        );
        verify(projectMapper, never()).update(any(Project.class), anyInt());
    }

    @Test
    void shouldRejectArchivedProjectReactivationBeforeDatabaseUpdate() {
        Project archived = project(
                PROJECT_ID,
                "Project",
                ProjectStatus.ARCHIVED,
                2,
                PREVIOUS_UPDATED_AT
        );
        when(projectMapper.findByIdForUpdate(PROJECT_ID))
                .thenReturn(Optional.of(archived));
        when(authorizationService.requireAtLeast(
                TEAM_ID,
                CURRENT_USER_ID,
                TeamRole.ADMIN
        )).thenReturn(TeamRole.OWNER);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> projectService.update(
                        CURRENT_USER_ID,
                        PROJECT_ID,
                        new UpdateProjectRequest(
                                "Project",
                                null,
                                ProjectStatus.ACTIVE,
                                2
                        )
                )
        );

        assertEquals(
                ProjectErrorCode.INVALID_STATUS_TRANSITION,
                exception.getErrorCode()
        );
        verify(projectMapper, never()).update(any(Project.class), anyInt());
    }

    @Test
    void shouldSkipDatabaseUpdateForIdempotentRequest() {
        Project project = activeProject(2);
        when(projectMapper.findByIdForUpdate(PROJECT_ID))
                .thenReturn(Optional.of(project));
        when(authorizationService.requireAtLeast(
                TEAM_ID,
                CURRENT_USER_ID,
                TeamRole.ADMIN
        )).thenReturn(TeamRole.ADMIN);

        ProjectResponse response = projectService.update(
                CURRENT_USER_ID,
                PROJECT_ID,
                new UpdateProjectRequest(
                        " Project ",
                        "   ",
                        ProjectStatus.ACTIVE,
                        2
                )
        );

        assertAll(
                () -> assertEquals(2, response.version()),
                () -> assertEquals(PREVIOUS_UPDATED_AT, response.updatedAt()),
                () -> assertNull(response.description())
        );
        verify(projectMapper, times(1)).findByIdForUpdate(PROJECT_ID);
        verify(projectMapper, never()).update(any(Project.class), anyInt());
    }

    @Test
    void shouldReportVersionConflictWhenConditionalUpdateLosesRace() {
        Project current = activeProject(2);
        when(projectMapper.findByIdForUpdate(PROJECT_ID))
                .thenReturn(Optional.of(current));
        prepareAdministratorAuthorization();
        when(projectMapper.update(any(Project.class), eq(2))).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> projectService.update(
                        CURRENT_USER_ID,
                        PROJECT_ID,
                        updateRequest(2)
                )
        );

        assertEquals(
                ProjectErrorCode.PROJECT_VERSION_CONFLICT,
                exception.getErrorCode()
        );
        verify(projectMapper, times(1)).findByIdForUpdate(PROJECT_ID);
    }

    @Test
    void shouldFailOnImpossibleMultiRowProjectUpdate() {
        when(projectMapper.findByIdForUpdate(PROJECT_ID))
                .thenReturn(Optional.of(activeProject(2)));
        prepareAdministratorAuthorization();
        when(projectMapper.update(any(Project.class), eq(2))).thenReturn(2);

        assertThrows(
                IllegalStateException.class,
                () -> projectService.update(
                        CURRENT_USER_ID,
                        PROJECT_ID,
                        updateRequest(2)
                )
        );

        verify(projectMapper, times(1)).findByIdForUpdate(PROJECT_ID);
    }

    @Test
    void shouldReportMissingProjectWhenSuccessfulUpdateCannotBeReloaded() {
        when(projectMapper.findByIdForUpdate(PROJECT_ID))
                .thenReturn(Optional.of(activeProject(2)));
        when(projectMapper.findById(PROJECT_ID))
                .thenReturn(Optional.empty());
        prepareAdministratorAuthorization();
        when(projectMapper.update(any(Project.class), eq(2))).thenReturn(1);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> projectService.update(
                        CURRENT_USER_ID,
                        PROJECT_ID,
                        updateRequest(2)
                )
        );

        assertEquals(
                ProjectErrorCode.PROJECT_NOT_FOUND,
                exception.getErrorCode()
        );
    }

    private void prepareSuccessfulCreate() {
        when(authorizationService.requireAtLeast(
                TEAM_ID,
                CURRENT_USER_ID,
                TeamRole.ADMIN
        )).thenReturn(TeamRole.ADMIN);
        when(idGenerator.nextId(ResourceType.PROJECT)).thenReturn(PROJECT_ID);
        when(projectMapper.countByTeamIdAndProjectKey(TEAM_ID, "TF"))
                .thenReturn(0);
        when(projectMapper.insert(any(Project.class))).thenReturn(1);
    }

    private void prepareAdministratorAuthorization() {
        when(authorizationService.requireAtLeast(
                TEAM_ID,
                CURRENT_USER_ID,
                TeamRole.ADMIN
        )).thenReturn(TeamRole.ADMIN);
    }

    private CreateProjectRequest createRequest() {
        return new CreateProjectRequest(
                TEAM_ID,
                "Project",
                "TF",
                null
        );
    }

    private UpdateProjectRequest updateRequest(int version) {
        return new UpdateProjectRequest(
                "Updated Project",
                "Updated description",
                ProjectStatus.ARCHIVED,
                version
        );
    }

    private Project activeProject(int version) {
        return project(
                PROJECT_ID,
                "Project",
                ProjectStatus.ACTIVE,
                version,
                PREVIOUS_UPDATED_AT
        );
    }

    private Project project(
            String id,
            String name,
            ProjectStatus status,
            int version,
            String updatedAt
    ) {
        return new Project(
                id,
                TEAM_ID,
                name,
                "TF",
                null,
                status,
                CURRENT_USER_ID,
                version,
                CREATED_AT,
                updatedAt
        );
    }

    private TeamMember currentMember() {
        return TeamMember.create(
                "mb001",
                TEAM_ID,
                CURRENT_USER_ID,
                TeamRole.MEMBER,
                CREATED_AT
        );
    }
}
