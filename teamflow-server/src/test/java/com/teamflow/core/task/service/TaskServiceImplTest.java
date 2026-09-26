package com.teamflow.core.task.service;

import com.teamflow.common.api.PageQuery;
import com.teamflow.common.api.PageResult;
import com.teamflow.common.error.BusinessException;
import com.teamflow.core.common.id.ReadableIdGenerator;
import com.teamflow.core.common.id.ResourceType;
import com.teamflow.core.project.domain.Project;
import com.teamflow.core.project.domain.ProjectStatus;
import com.teamflow.core.task.domain.Task;
import com.teamflow.core.task.domain.TaskPriority;
import com.teamflow.core.task.domain.TaskStatus;
import com.teamflow.core.task.dto.ChangeTaskStatusRequest;
import com.teamflow.core.task.dto.CreateTaskRequest;
import com.teamflow.core.task.dto.TaskResponse;
import com.teamflow.core.task.dto.UpdateTaskRequest;
import com.teamflow.core.task.error.TaskErrorCode;
import com.teamflow.core.task.event.TaskAssignmentChanged;
import com.teamflow.core.task.mapper.TaskMapper;
import com.teamflow.core.task.service.impl.TaskServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 任务业务、状态命令、软删除和事务 Outbox 测试。 */
@ExtendWith(MockitoExtension.class)
class TaskServiceImplTest {

    private static final String USER_ID = "u001";
    private static final String ASSIGNEE_ID = "u002";
    private static final String NEW_ASSIGNEE_ID = "u003";
    private static final String TEAM_ID = "tm001";
    private static final String PROJECT_ID = "p001";
    private static final String TASK_ID = "t001";
    private static final String CREATED_AT = "2026-09-15T01:02:03.456Z";
    private static final String NOW = "2026-09-16T02:03:04.567Z";

    @Mock
    private TaskMapper taskMapper;
    @Mock
    private TaskAccessService taskAccessService;
    @Mock
    private ReadableIdGenerator idGenerator;
    @Mock
    private TaskEventPublisher eventPublisher;

    private TaskServiceImpl taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskServiceImpl(
                taskMapper,
                taskAccessService,
                idGenerator,
                eventPublisher,
                Clock.fixed(Instant.parse(NOW), ZoneOffset.UTC)
        );
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void shouldRequireEveryDependency() {
        Clock clock = Clock.systemUTC();
        assertAll(
                () -> assertThrows(NullPointerException.class, () ->
                        new TaskServiceImpl(
                                null, taskAccessService, idGenerator,
                                eventPublisher, clock
                        )),
                () -> assertThrows(NullPointerException.class, () ->
                        new TaskServiceImpl(
                                taskMapper, null, idGenerator,
                                eventPublisher, clock
                        )),
                () -> assertThrows(NullPointerException.class, () ->
                        new TaskServiceImpl(
                                taskMapper, taskAccessService, null,
                                eventPublisher, clock
                        )),
                () -> assertThrows(NullPointerException.class, () ->
                        new TaskServiceImpl(
                                taskMapper, taskAccessService, idGenerator,
                                null, clock
                        )),
                () -> assertThrows(NullPointerException.class, () ->
                        new TaskServiceImpl(
                                taskMapper, taskAccessService, idGenerator,
                                eventPublisher, null
                        ))
        );
    }

    @Test
    void shouldCreateTaskAndPublishCompleteAssignmentEvent() {
        CreateTaskRequest request = createRequest(ASSIGNEE_ID);
        when(taskAccessService.requireProjectMemberForUpdate(
                USER_ID, PROJECT_ID, ASSIGNEE_ID
        )).thenReturn(project(ProjectStatus.ACTIVE));
        when(idGenerator.nextId(ResourceType.TASK)).thenReturn(TASK_ID);
        when(taskMapper.insert(any(Task.class))).thenReturn(1);

        TaskResponse response = taskService.create(USER_ID, request);

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        ArgumentCaptor<TaskAssignmentChanged> eventCaptor =
                ArgumentCaptor.forClass(TaskAssignmentChanged.class);
        InOrder order = inOrder(
                taskAccessService,
                idGenerator,
                taskMapper,
                eventPublisher
        );
        order.verify(taskAccessService).requireProjectMemberForUpdate(
                USER_ID, PROJECT_ID, ASSIGNEE_ID
        );
        order.verify(idGenerator).nextId(ResourceType.TASK);
        order.verify(taskMapper).insert(taskCaptor.capture());
        order.verify(eventPublisher)
                .publishAssignmentChanged(eventCaptor.capture());

        Task saved = taskCaptor.getValue();
        TaskAssignmentChanged event = eventCaptor.getValue();
        assertAll(
                () -> assertEquals(TASK_ID, saved.getId()),
                () -> assertEquals(USER_ID, saved.getReporterId()),
                () -> assertEquals(TaskStatus.TODO, saved.getStatus()),
                () -> assertEquals(0, saved.getVersion()),
                () -> assertEquals(NOW, saved.getCreatedAt()),
                () -> assertEquals(TASK_ID, response.id()),
                () -> assertEquals(ASSIGNEE_ID, response.assigneeId()),
                () -> assertEquals(TASK_ID, event.taskId()),
                () -> assertNull(event.previousAssigneeId()),
                () -> assertEquals(ASSIGNEE_ID, event.assigneeId()),
                () -> assertEquals(USER_ID, event.operatorId()),
                () -> assertEquals(0, event.taskVersion()),
                () -> assertEquals(NOW, event.occurredAt())
        );
    }

    @Test
    void shouldNotPublishWhenCreatedWithoutAssignee() {
        when(taskAccessService.requireProjectMemberForUpdate(
                USER_ID, PROJECT_ID, null
        )).thenReturn(project(ProjectStatus.ACTIVE));
        when(idGenerator.nextId(ResourceType.TASK)).thenReturn(TASK_ID);
        when(taskMapper.insert(any(Task.class))).thenReturn(1);

        TaskResponse response = taskService.create(
                USER_ID,
                createRequest(null)
        );

        assertNull(response.assigneeId());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldRejectArchivedProjectBeforeAllocatingId() {
        when(taskAccessService.requireProjectMemberForUpdate(
                USER_ID, PROJECT_ID, null
        )).thenReturn(project(ProjectStatus.ARCHIVED));

        assertBusinessError(
                TaskErrorCode.PROJECT_NOT_ACTIVE,
                () -> taskService.create(USER_ID, createRequest(null))
        );

        verifyNoInteractions(idGenerator, taskMapper, eventPublisher);
    }

    @Test
    void shouldRejectImpossibleInsertCount() {
        when(taskAccessService.requireProjectMemberForUpdate(
                USER_ID, PROJECT_ID, null
        )).thenReturn(project(ProjectStatus.ACTIVE));
        when(idGenerator.nextId(ResourceType.TASK)).thenReturn(TASK_ID);
        when(taskMapper.insert(any(Task.class))).thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> taskService.create(USER_ID, createRequest(null))
        );
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldListFilteredPageUsingLimitAndOffset() {
        PageQuery query = new PageQuery(3, 20);
        Task first = task(TaskStatus.TODO, null, 0, CREATED_AT);
        when(taskAccessService.requireProjectMember(USER_ID, PROJECT_ID))
                .thenReturn(project(ProjectStatus.ARCHIVED));
        when(taskMapper.findByProjectId(
                PROJECT_ID, TaskStatus.TODO, 20, 40L
        )).thenReturn(List.of(first));
        when(taskMapper.countByProjectId(PROJECT_ID, TaskStatus.TODO))
                .thenReturn(41L);

        PageResult<TaskResponse> result = taskService.listByProject(
                USER_ID,
                PROJECT_ID,
                TaskStatus.TODO,
                query
        );

        assertAll(
                () -> assertEquals(1, result.items().size()),
                () -> assertEquals(TASK_ID, result.items().getFirst().id()),
                () -> assertEquals(3, result.page()),
                () -> assertEquals(20, result.size()),
                () -> assertEquals(41, result.total())
        );
    }

    @Test
    void shouldGetTaskFromSharedAccessBoundary() {
        Task task = task(TaskStatus.IN_PROGRESS, ASSIGNEE_ID, 2, CREATED_AT);
        when(taskAccessService.requireTaskMember(USER_ID, TASK_ID))
                .thenReturn(new TaskAccessContext(
                        task,
                        project(ProjectStatus.ARCHIVED)
                ));

        TaskResponse response = taskService.get(USER_ID, TASK_ID);

        assertAll(
                () -> assertEquals(TASK_ID, response.id()),
                () -> assertEquals(TaskStatus.IN_PROGRESS, response.status()),
                () -> assertEquals(2, response.version())
        );
    }

    @Test
    void shouldUpdateWithObservedVersionAndPublishAssigneeChange() {
        Task current = task(
                TaskStatus.IN_PROGRESS,
                ASSIGNEE_ID,
                2,
                CREATED_AT
        );
        Task saved = new Task(
                TASK_ID, PROJECT_ID, "新标题", "新说明",
                TaskStatus.DONE, TaskPriority.URGENT, NEW_ASSIGNEE_ID,
                USER_ID, NOW, 3, CREATED_AT, NOW
        );
        UpdateTaskRequest request = new UpdateTaskRequest(
                " 新标题 ", " 新说明 ", TaskStatus.DONE,
                TaskPriority.URGENT, NEW_ASSIGNEE_ID, NOW, 2
        );
        when(taskAccessService.requireTaskMemberForUpdate(
                USER_ID, TASK_ID, NEW_ASSIGNEE_ID
        )).thenReturn(activeAccess(current));
        when(taskMapper.update(any(Task.class), eq(2))).thenReturn(1);
        when(taskMapper.findById(TASK_ID)).thenReturn(Optional.of(saved));

        TaskResponse response = taskService.update(USER_ID, TASK_ID, request);

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        ArgumentCaptor<TaskAssignmentChanged> eventCaptor =
                ArgumentCaptor.forClass(TaskAssignmentChanged.class);
        verify(taskMapper).update(taskCaptor.capture(), eq(2));
        verify(eventPublisher).publishAssignmentChanged(eventCaptor.capture());
        assertAll(
                () -> assertEquals(3, taskCaptor.getValue().getVersion()),
                () -> assertEquals(NEW_ASSIGNEE_ID,
                        taskCaptor.getValue().getAssigneeId()),
                () -> assertEquals(3, response.version()),
                () -> assertEquals(ASSIGNEE_ID,
                        eventCaptor.getValue().previousAssigneeId()),
                () -> assertEquals(NEW_ASSIGNEE_ID,
                        eventCaptor.getValue().assigneeId()),
                () -> assertEquals(3,
                        eventCaptor.getValue().taskVersion())
        );
    }

    @Test
    void shouldSkipDatabaseWriteForIdempotentLockedUpdate() {
        Task current = task(TaskStatus.TODO, null, 2, CREATED_AT);
        when(taskAccessService.requireTaskMemberForUpdate(
                USER_ID, TASK_ID, null
        )).thenReturn(activeAccess(current));

        TaskResponse response = taskService.update(
                USER_ID,
                TASK_ID,
                new UpdateTaskRequest(
                        " Task ", null, TaskStatus.TODO,
                        TaskPriority.MEDIUM, null, null, 2
                )
        );

        assertEquals(2, response.version());
        verify(taskMapper, never()).update(any(Task.class), eq(2));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldRejectStaleVersionBeforeDatabaseWrite() {
        Task current = task(TaskStatus.TODO, null, 2, CREATED_AT);
        when(taskAccessService.requireTaskMemberForUpdate(
                USER_ID, TASK_ID, null
        )).thenReturn(activeAccess(current));

        assertBusinessError(
                TaskErrorCode.TASK_VERSION_CONFLICT,
                () -> taskService.update(
                        USER_ID,
                        TASK_ID,
                        updateRequest(TaskStatus.IN_PROGRESS, 1)
                )
        );

        verify(taskMapper, never()).update(any(Task.class), anyInt());
    }

    @Test
    void shouldRejectNonAdjacentStatusTransition() {
        Task current = task(TaskStatus.TODO, null, 2, CREATED_AT);
        when(taskAccessService.requireTaskMemberForUpdate(
                USER_ID, TASK_ID, null
        )).thenReturn(activeAccess(current));

        assertBusinessError(
                TaskErrorCode.INVALID_STATUS_TRANSITION,
                () -> taskService.update(
                        USER_ID,
                        TASK_ID,
                        updateRequest(TaskStatus.DONE, 2)
                )
        );

        verify(taskMapper, never()).update(any(Task.class), anyInt());
    }

    @Test
    void shouldConvertLostUpdateToConflictWithoutEvent() {
        Task current = task(TaskStatus.TODO, null, 2, CREATED_AT);
        when(taskAccessService.requireTaskMemberForUpdate(
                USER_ID, TASK_ID, null
        )).thenReturn(activeAccess(current));
        when(taskMapper.update(any(Task.class), eq(2))).thenReturn(0);

        assertBusinessError(
                TaskErrorCode.TASK_VERSION_CONFLICT,
                () -> taskService.update(
                        USER_ID,
                        TASK_ID,
                        updateRequest(TaskStatus.IN_PROGRESS, 2)
                )
        );

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldNotPublishWhenOnlyOtherFieldsChange() {
        Task current = task(TaskStatus.TODO, ASSIGNEE_ID, 0, CREATED_AT);
        Task saved = new Task(
                TASK_ID, PROJECT_ID, "新标题", null,
                TaskStatus.IN_PROGRESS, TaskPriority.MEDIUM,
                ASSIGNEE_ID, USER_ID, null, 1, CREATED_AT, NOW
        );
        when(taskAccessService.requireTaskMemberForUpdate(
                USER_ID, TASK_ID, ASSIGNEE_ID
        )).thenReturn(activeAccess(current));
        when(taskMapper.update(any(Task.class), eq(0))).thenReturn(1);
        when(taskMapper.findById(TASK_ID)).thenReturn(Optional.of(saved));

        taskService.update(
                USER_ID,
                TASK_ID,
                new UpdateTaskRequest(
                        "新标题", null, TaskStatus.IN_PROGRESS,
                        TaskPriority.MEDIUM, ASSIGNEE_ID, null, 0
                )
        );

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldSoftDeleteOnlyMatchingVersion() {
        Task current = task(TaskStatus.TODO, null, 4, CREATED_AT);
        when(taskAccessService.requireTaskMemberForUpdate(USER_ID, TASK_ID))
                .thenReturn(activeAccess(current));
        when(taskMapper.softDelete(TASK_ID, 4, NOW)).thenReturn(1);

        taskService.delete(USER_ID, TASK_ID, 4);

        verify(taskMapper).softDelete(TASK_ID, 4, NOW);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldReportConflictWhenSoftDeleteLosesRace() {
        Task current = task(TaskStatus.TODO, null, 4, CREATED_AT);
        when(taskAccessService.requireTaskMemberForUpdate(USER_ID, TASK_ID))
                .thenReturn(activeAccess(current));
        when(taskMapper.softDelete(TASK_ID, 4, NOW)).thenReturn(0);

        assertBusinessError(
                TaskErrorCode.TASK_VERSION_CONFLICT,
                () -> taskService.delete(USER_ID, TASK_ID, 4)
        );
    }

    @Test
    void shouldChangeOnlyStatusForBoardMove() {
        Task current = task(TaskStatus.TODO, ASSIGNEE_ID, 1, CREATED_AT);
        Task saved = new Task(
                TASK_ID, PROJECT_ID, "Task", null,
                TaskStatus.IN_PROGRESS, TaskPriority.MEDIUM,
                ASSIGNEE_ID, USER_ID, null, 2, CREATED_AT, NOW
        );
        when(taskAccessService.requireTaskMemberForUpdate(USER_ID, TASK_ID))
                .thenReturn(activeAccess(current));
        when(taskMapper.update(any(Task.class), eq(1))).thenReturn(1);
        when(taskMapper.findById(TASK_ID)).thenReturn(Optional.of(saved));

        TaskResponse response = taskService.changeStatus(
                USER_ID,
                TASK_ID,
                new ChangeTaskStatusRequest(TaskStatus.IN_PROGRESS, 1)
        );

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).update(captor.capture(), eq(1));
        assertAll(
                () -> assertEquals(TaskStatus.IN_PROGRESS,
                        response.status()),
                () -> assertEquals(2, response.version()),
                () -> assertEquals("Task", captor.getValue().getTitle()),
                () -> assertEquals(ASSIGNEE_ID,
                        captor.getValue().getAssigneeId())
        );
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldWriteAssignmentEventInsideActiveTransaction() {
        beginSynchronization();
        prepareCreateWithAssignee();

        taskService.create(USER_ID, createRequest(ASSIGNEE_ID));

        verify(eventPublisher).publishAssignmentChanged(any());
        assertEquals(
                0,
                TransactionSynchronizationManager
                        .getSynchronizations()
                        .size()
        );
    }

    @Test
    void shouldPropagateOutboxWriteFailureToBusinessTransaction() {
        beginSynchronization();
        prepareCreateWithAssignee();
        doThrow(new IllegalStateException("outbox unavailable"))
                .when(eventPublisher)
                .publishAssignmentChanged(any());

        assertThrows(
                IllegalStateException.class,
                () -> taskService.create(
                        USER_ID,
                        createRequest(ASSIGNEE_ID)
                )
        );
    }

    private void prepareCreateWithAssignee() {
        when(taskAccessService.requireProjectMemberForUpdate(
                USER_ID, PROJECT_ID, ASSIGNEE_ID
        )).thenReturn(project(ProjectStatus.ACTIVE));
        when(idGenerator.nextId(ResourceType.TASK)).thenReturn(TASK_ID);
        when(taskMapper.insert(any(Task.class))).thenReturn(1);
    }

    private void beginSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private CreateTaskRequest createRequest(String assigneeId) {
        return new CreateTaskRequest(
                PROJECT_ID,
                " Task ",
                null,
                TaskPriority.MEDIUM,
                assigneeId,
                null
        );
    }

    private UpdateTaskRequest updateRequest(
            TaskStatus status,
            int version
    ) {
        return new UpdateTaskRequest(
                "Changed",
                null,
                status,
                TaskPriority.MEDIUM,
                null,
                null,
                version
        );
    }

    private TaskAccessContext activeAccess(Task task) {
        return new TaskAccessContext(task, project(ProjectStatus.ACTIVE));
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
                CREATED_AT,
                CREATED_AT
        );
    }

    private Task task(
            TaskStatus status,
            String assigneeId,
            int version,
            String updatedAt
    ) {
        return new Task(
                TASK_ID,
                PROJECT_ID,
                "Task",
                null,
                status,
                TaskPriority.MEDIUM,
                assigneeId,
                USER_ID,
                null,
                version,
                CREATED_AT,
                updatedAt
        );
    }

    private void assertBusinessError(
            TaskErrorCode expected,
            Runnable invocation
    ) {
        BusinessException exception = assertThrows(
                BusinessException.class,
                invocation::run
        );
        assertSame(expected, exception.getErrorCode());
    }
}
