package com.teamflow.core.dashboard.service;

import com.teamflow.common.error.BusinessException;
import com.teamflow.core.dashboard.dto.ProjectDashboardResponse;
import com.teamflow.core.dashboard.error.DashboardErrorCode;
import com.teamflow.core.dashboard.service.impl.DashboardServiceImpl;
import com.teamflow.core.project.domain.Project;
import com.teamflow.core.project.domain.ProjectStatus;
import com.teamflow.core.project.error.ProjectErrorCode;
import com.teamflow.core.project.mapper.ProjectMapper;
import com.teamflow.core.task.domain.TaskStatus;
import com.teamflow.core.task.mapper.AssigneeCompletedCount;
import com.teamflow.core.task.mapper.TaskMapper;
import com.teamflow.core.task.mapper.TaskStatusCount;
import com.teamflow.core.team.error.TeamErrorCode;
import com.teamflow.core.team.service.TeamAuthorizationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 仪表盘并行查询、权限、超时和过载策略测试。 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    private static final String NOW = "2026-09-18T03:04:05.678Z";
    private static final String PROJECT_ID = "p001";
    private static final String TEAM_ID = "tm001";
    private static final String CURRENT_USER_ID = "u001";

    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private TeamAuthorizationService authorizationService;

    private ExecutorService executorService;

    @AfterEach
    void shutDownExecutor() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    @Test
    void shouldRequireEveryDependency() {
        Executor executor = Runnable::run;
        Clock clock = fixedClock();

        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new DashboardServiceImpl(
                                null, taskMapper, authorizationService,
                                executor, clock
                        )
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new DashboardServiceImpl(
                                projectMapper, null, authorizationService,
                                executor, clock
                        )
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new DashboardServiceImpl(
                                projectMapper, taskMapper, null,
                                executor, clock
                        )
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new DashboardServiceImpl(
                                projectMapper, taskMapper,
                                authorizationService, null, clock
                        )
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new DashboardServiceImpl(
                                projectMapper, taskMapper,
                                authorizationService, executor, null
                        )
                )
        );
    }

    @Test
    void shouldRunAllStatisticsInParallelOnProvidedExecutor() throws Exception {
        prepareAuthorizedProject();
        executorService = Executors.newFixedThreadPool(
                3,
                Thread.ofPlatform()
                        .name("dashboard-test-worker-", 0)
                        .factory()
        );
        CyclicBarrier startedTogether = new CyclicBarrier(3);
        Set<String> queryThreads = ConcurrentHashMap.newKeySet();
        when(taskMapper.countByStatus(PROJECT_ID)).thenAnswer(invocation -> {
            awaitQueries(startedTogether, queryThreads);
            return List.of(
                    new TaskStatusCount(TaskStatus.TODO, 2),
                    new TaskStatusCount(TaskStatus.DONE, 1)
            );
        });
        when(taskMapper.countOverdue(PROJECT_ID, NOW)).thenAnswer(invocation -> {
            awaitQueries(startedTogether, queryThreads);
            return 1L;
        });
        when(taskMapper.countCompletedByAssignee(PROJECT_ID))
                .thenAnswer(invocation -> {
                    awaitQueries(startedTogether, queryThreads);
                    return List.of(
                            new AssigneeCompletedCount("u002", 1)
                    );
                });

        ProjectDashboardResponse response = service(executorService)
                .getProjectDashboard(CURRENT_USER_ID, PROJECT_ID);

        assertAll(
                () -> assertEquals(PROJECT_ID, response.projectId()),
                () -> assertEquals(3L, response.totalTasks()),
                () -> assertEquals(1L, response.overdueTasks()),
                () -> assertEquals(
                        Map.of(
                                "TODO", 2L,
                                "IN_PROGRESS", 0L,
                                "DONE", 1L
                        ),
                        response.statusCounts()
                ),
                () -> assertEquals(
                        Map.of("u002", 1L),
                        response.memberCompletedCounts()
                ),
                () -> assertEquals(NOW, response.generatedAt()),
                () -> assertEquals(3, queryThreads.size()),
                () -> assertTrue(
                        queryThreads.stream().allMatch(
                                name -> name.startsWith("dashboard-test-")
                        )
                )
        );
        verify(authorizationService).requireMember(
                TEAM_ID,
                CURRENT_USER_ID
        );
    }

    @Test
    void shouldFailWholeSnapshotWhenOneStatisticTimesOut() {
        prepareAuthorizedProject();
        AtomicInteger submissions = new AtomicInteger();
        Executor oneStalledTaskExecutor = command -> {
            if (submissions.getAndIncrement() == 0) {
                return;
            }
            command.run();
        };
        when(taskMapper.countOverdue(PROJECT_ID, NOW)).thenReturn(0L);
        when(taskMapper.countCompletedByAssignee(PROJECT_ID))
                .thenReturn(List.of());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service(oneStalledTaskExecutor)
                        .getProjectDashboard(CURRENT_USER_ID, PROJECT_ID)
        );

        assertEquals(
                DashboardErrorCode.STATISTICS_TIMEOUT,
                exception.getErrorCode()
        );
        verify(taskMapper).countOverdue(PROJECT_ID, NOW);
        verify(taskMapper).countCompletedByAssignee(PROJECT_ID);
    }

    @Test
    void shouldTranslateStatisticFailureWithoutReturningFalseZeroes() {
        prepareAuthorizedProject();
        when(taskMapper.countByStatus(PROJECT_ID)).thenReturn(List.of());
        when(taskMapper.countOverdue(PROJECT_ID, NOW))
                .thenThrow(new IllegalStateException("database unavailable"));
        when(taskMapper.countCompletedByAssignee(PROJECT_ID))
                .thenReturn(List.of());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service(Runnable::run)
                        .getProjectDashboard(CURRENT_USER_ID, PROJECT_ID)
        );

        assertEquals(
                DashboardErrorCode.STATISTICS_UNAVAILABLE,
                exception.getErrorCode()
        );
    }

    @Test
    void shouldRestoreBusinessExceptionFromAsyncQuery() {
        prepareAuthorizedProject();
        BusinessException expected = new BusinessException(
                TeamErrorCode.INSUFFICIENT_PERMISSION
        );
        when(taskMapper.countByStatus(PROJECT_ID)).thenThrow(expected);
        when(taskMapper.countOverdue(PROJECT_ID, NOW)).thenReturn(0L);
        when(taskMapper.countCompletedByAssignee(PROJECT_ID))
                .thenReturn(List.of());

        BusinessException actual = assertThrows(
                BusinessException.class,
                () -> service(Runnable::run)
                        .getProjectDashboard(CURRENT_USER_ID, PROJECT_ID)
        );

        assertSame(expected, actual);
    }

    @Test
    void shouldReportBusyWhenExecutorRejectsSubmission() {
        prepareAuthorizedProject();
        AtomicInteger submissions = new AtomicInteger();
        Executor rejectingExecutor = command -> {
            if (submissions.getAndIncrement() == 1) {
                throw new RejectedExecutionException("queue full");
            }
            command.run();
        };
        when(taskMapper.countByStatus(PROJECT_ID)).thenReturn(List.of());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service(rejectingExecutor)
                        .getProjectDashboard(CURRENT_USER_ID, PROJECT_ID)
        );

        assertEquals(
                DashboardErrorCode.EXECUTOR_BUSY,
                exception.getErrorCode()
        );
        verify(taskMapper).countByStatus(PROJECT_ID);
        verify(taskMapper, never()).countCompletedByAssignee(PROJECT_ID);
    }

    @Test
    void shouldRejectInvisibleProjectBeforeSubmittingStatistics() {
        when(projectMapper.findById(PROJECT_ID))
                .thenReturn(Optional.of(project()));
        doThrow(new BusinessException(TeamErrorCode.TEAM_NOT_FOUND))
                .when(authorizationService)
                .requireMember(TEAM_ID, CURRENT_USER_ID);
        Executor executor = org.mockito.Mockito.mock(Executor.class);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service(executor)
                        .getProjectDashboard(CURRENT_USER_ID, PROJECT_ID)
        );

        assertEquals(ProjectErrorCode.PROJECT_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(taskMapper, executor);
    }

    @Test
    void shouldReportMissingProjectWithoutCheckingMembershipOrStatistics() {
        when(projectMapper.findById(PROJECT_ID)).thenReturn(Optional.empty());
        Executor executor = org.mockito.Mockito.mock(Executor.class);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service(executor)
                        .getProjectDashboard(CURRENT_USER_ID, PROJECT_ID)
        );

        assertEquals(ProjectErrorCode.PROJECT_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(authorizationService, taskMapper, executor);
    }

    private DashboardServiceImpl service(Executor executor) {
        return new DashboardServiceImpl(
                projectMapper,
                taskMapper,
                authorizationService,
                executor,
                fixedClock()
        );
    }

    private void prepareAuthorizedProject() {
        when(projectMapper.findById(PROJECT_ID))
                .thenReturn(Optional.of(project()));
    }

    private static Project project() {
        return new Project(
                PROJECT_ID,
                TEAM_ID,
                "TeamFlow",
                "TF",
                null,
                ProjectStatus.ACTIVE,
                CURRENT_USER_ID,
                0,
                "2026-09-17T03:04:05.678Z",
                "2026-09-17T03:04:05.678Z"
        );
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse(NOW), ZoneOffset.UTC);
    }

    private static void awaitQueries(
            CyclicBarrier barrier,
            Set<String> queryThreads
    ) throws Exception {
        queryThreads.add(Thread.currentThread().getName());
        barrier.await(1, TimeUnit.SECONDS);
    }
}
