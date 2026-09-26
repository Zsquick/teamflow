package com.teamflow.core.task.service;

import com.teamflow.common.error.BusinessException;
import com.teamflow.core.project.domain.Project;
import com.teamflow.core.project.domain.ProjectStatus;
import com.teamflow.core.project.error.ProjectErrorCode;
import com.teamflow.core.project.mapper.ProjectMapper;
import com.teamflow.core.task.domain.Task;
import com.teamflow.core.task.domain.TaskPriority;
import com.teamflow.core.task.domain.TaskStatus;
import com.teamflow.core.task.error.TaskErrorCode;
import com.teamflow.core.task.mapper.TaskMapper;
import com.teamflow.core.task.service.impl.TaskAccessServiceImpl;
import com.teamflow.core.team.error.TeamErrorCode;
import com.teamflow.core.team.service.TeamAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 任务、项目与团队成员访问边界测试。 */
@ExtendWith(MockitoExtension.class)
class TaskAccessServiceImplTest {

    private static final String USER_ID = "u000001";
    private static final String TEAM_ID = "tm000001";
    private static final String PROJECT_ID = "p000001";
    private static final String TASK_ID = "t000001";
    private static final String ASSIGNEE_ID = "u000002";
    private static final String NOW = "2026-09-16T01:02:03.456Z";

    @Mock
    private TaskMapper taskMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private TeamAuthorizationService authorizationService;

    private TaskAccessServiceImpl accessService;

    @BeforeEach
    void setUp() {
        accessService = new TaskAccessServiceImpl(
                taskMapper,
                projectMapper,
                authorizationService
        );
    }

    @Test
    void shouldRequireEveryDependency() {
        assertAll(
                () -> assertThrows(NullPointerException.class, () ->
                        new TaskAccessServiceImpl(
                                null, projectMapper, authorizationService
                        )),
                () -> assertThrows(NullPointerException.class, () ->
                        new TaskAccessServiceImpl(
                                taskMapper, null, authorizationService
                        )),
                () -> assertThrows(NullPointerException.class, () ->
                        new TaskAccessServiceImpl(
                                taskMapper, projectMapper, null
                        ))
        );
    }

    @Test
    void shouldLoadProjectThenAuthorizeReadOnlyMembership() {
        Project project = project(ProjectStatus.ACTIVE);
        when(projectMapper.findById(PROJECT_ID)).thenReturn(Optional.of(project));

        Project actual = accessService.requireProjectMember(USER_ID, PROJECT_ID);

        assertSame(project, actual);
        InOrder order = inOrder(projectMapper, authorizationService);
        order.verify(projectMapper).findById(PROJECT_ID);
        order.verify(authorizationService).requireMember(TEAM_ID, USER_ID);
        verify(authorizationService, never()).requireMembersForUpdate(
                TEAM_ID, USER_ID, null
        );
    }

    @Test
    void shouldUseLockedMembershipCheckForProjectWrite() {
        Project project = project(ProjectStatus.ACTIVE);
        when(projectMapper.findByIdForUpdate(PROJECT_ID))
                .thenReturn(Optional.of(project));

        assertSame(
                project,
                accessService.requireProjectMemberForUpdate(
                        USER_ID,
                        PROJECT_ID,
                        ASSIGNEE_ID
                )
        );
        InOrder order = inOrder(projectMapper, authorizationService);
        order.verify(projectMapper).findByIdForUpdate(PROJECT_ID);
        order.verify(authorizationService).requireMembersForUpdate(
                TEAM_ID,
                USER_ID,
                ASSIGNEE_ID
        );
        verify(authorizationService, never()).requireMember(TEAM_ID, USER_ID);
    }

    @Test
    void shouldReportMissingProjectBeforeAuthorization() {
        when(projectMapper.findById(PROJECT_ID)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> accessService.requireProjectMember(USER_ID, PROJECT_ID)
        );

        assertEquals(ProjectErrorCode.PROJECT_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(authorizationService, taskMapper);
    }

    @Test
    void shouldHideMissingProjectMembershipAsProjectNotFound() {
        when(projectMapper.findById(PROJECT_ID))
                .thenReturn(Optional.of(project(ProjectStatus.ACTIVE)));
        when(authorizationService.requireMember(TEAM_ID, USER_ID))
                .thenThrow(new BusinessException(TeamErrorCode.TEAM_NOT_FOUND));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> accessService.requireProjectMember(USER_ID, PROJECT_ID)
        );

        assertEquals(ProjectErrorCode.PROJECT_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void shouldLoadTaskProjectAndMembershipInOrderForRead() {
        Task task = task();
        Project project = project(ProjectStatus.ACTIVE);
        when(taskMapper.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(projectMapper.findById(PROJECT_ID)).thenReturn(Optional.of(project));

        TaskAccessContext context = accessService.requireTaskMember(
                USER_ID,
                TASK_ID
        );

        assertAll(
                () -> assertSame(task, context.task()),
                () -> assertSame(project, context.project())
        );
        InOrder order = inOrder(
                taskMapper,
                projectMapper,
                authorizationService
        );
        order.verify(taskMapper).findById(TASK_ID);
        order.verify(projectMapper).findById(PROJECT_ID);
        order.verify(authorizationService).requireMember(TEAM_ID, USER_ID);
    }

    @Test
    void shouldUseLockedMembershipCheckForTaskWrite() {
        Task task = task();
        when(taskMapper.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(projectMapper.findByIdForUpdate(PROJECT_ID))
                .thenReturn(Optional.of(project(ProjectStatus.ACTIVE)));
        when(taskMapper.findByIdForUpdate(TASK_ID))
                .thenReturn(Optional.of(task));

        accessService.requireTaskMemberForUpdate(
                USER_ID,
                TASK_ID,
                ASSIGNEE_ID
        );

        InOrder order = inOrder(
                taskMapper,
                projectMapper,
                authorizationService
        );
        order.verify(taskMapper).findById(TASK_ID);
        order.verify(projectMapper).findByIdForUpdate(PROJECT_ID);
        order.verify(taskMapper).findByIdForUpdate(TASK_ID);
        order.verify(authorizationService).requireMembersForUpdate(
                TEAM_ID,
                USER_ID,
                ASSIGNEE_ID
        );
        verify(authorizationService, never()).requireMember(TEAM_ID, USER_ID);
    }

    @Test
    void shouldMapMissingRelatedMemberToAssigneeError() {
        when(projectMapper.findByIdForUpdate(PROJECT_ID))
                .thenReturn(Optional.of(project(ProjectStatus.ACTIVE)));
        BusinessException missing = new BusinessException(
                TeamErrorCode.MEMBER_NOT_FOUND
        );
        org.mockito.Mockito.doThrow(missing)
                .when(authorizationService)
                .requireMembersForUpdate(
                        TEAM_ID,
                        USER_ID,
                        ASSIGNEE_ID
                );

        BusinessException actual = assertThrows(
                BusinessException.class,
                () -> accessService.requireProjectMemberForUpdate(
                        USER_ID,
                        PROJECT_ID,
                        ASSIGNEE_ID
                )
        );

        assertEquals(
                TaskErrorCode.ASSIGNEE_NOT_TEAM_MEMBER,
                actual.getErrorCode()
        );
    }

    @Test
    void shouldReportTaskNotFoundWithoutLoadingProject() {
        when(taskMapper.findById(TASK_ID)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> accessService.requireTaskMember(USER_ID, TASK_ID)
        );

        assertEquals(TaskErrorCode.TASK_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(projectMapper, authorizationService);
    }

    @Test
    void shouldHideMissingTaskProjectOrMembershipAsTaskNotFound() {
        when(taskMapper.findById(TASK_ID)).thenReturn(Optional.of(task()));
        when(projectMapper.findById(PROJECT_ID)).thenReturn(Optional.empty());

        BusinessException missingProject = assertThrows(
                BusinessException.class,
                () -> accessService.requireTaskMember(USER_ID, TASK_ID)
        );
        assertEquals(TaskErrorCode.TASK_NOT_FOUND, missingProject.getErrorCode());

        when(projectMapper.findById(PROJECT_ID))
                .thenReturn(Optional.of(project(ProjectStatus.ACTIVE)));
        when(authorizationService.requireMember(TEAM_ID, USER_ID))
                .thenThrow(new BusinessException(TeamErrorCode.TEAM_NOT_FOUND));

        BusinessException missingMembership = assertThrows(
                BusinessException.class,
                () -> accessService.requireTaskMember(USER_ID, TASK_ID)
        );
        assertEquals(TaskErrorCode.TASK_NOT_FOUND, missingMembership.getErrorCode());
    }

    @Test
    void shouldPreserveNonMissingAuthorizationErrors() {
        BusinessException denied = new BusinessException(
                TeamErrorCode.INSUFFICIENT_PERMISSION
        );
        when(projectMapper.findById(PROJECT_ID))
                .thenReturn(Optional.of(project(ProjectStatus.ACTIVE)));
        when(authorizationService.requireMember(TEAM_ID, USER_ID))
                .thenThrow(denied);

        assertSame(
                denied,
                assertThrows(
                        BusinessException.class,
                        () -> accessService.requireProjectMember(
                                USER_ID,
                                PROJECT_ID
                        )
                )
        );
    }

    @Test
    void shouldRejectMismatchedTaskAccessContext() {
        Task task = task();
        Project otherProject = new Project(
                "p000002", TEAM_ID, "其他项目", "OTHER", null,
                ProjectStatus.ACTIVE, USER_ID, 0, NOW, NOW
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new TaskAccessContext(task, otherProject)
        );
    }

    private Project project(ProjectStatus status) {
        return new Project(
                PROJECT_ID,
                TEAM_ID,
                "TeamFlow",
                "TF",
                null,
                status,
                USER_ID,
                0,
                NOW,
                NOW
        );
    }

    private Task task() {
        return new Task(
                TASK_ID,
                PROJECT_ID,
                "任务",
                null,
                TaskStatus.TODO,
                TaskPriority.HIGH,
                null,
                USER_ID,
                null,
                0,
                NOW,
                NOW
        );
    }
}
