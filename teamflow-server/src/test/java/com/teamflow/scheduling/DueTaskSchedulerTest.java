package com.teamflow.scheduling;

import com.teamflow.core.notification.dto.CreateNotificationCommand;
import com.teamflow.core.notification.service.NotificationService;
import com.teamflow.core.task.domain.Task;
import com.teamflow.core.task.domain.TaskPriority;
import com.teamflow.core.task.domain.TaskStatus;
import com.teamflow.core.task.mapper.TaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 到期提醒窗口、幂等事件键、批次上限和 Redis 租约测试。 */
class DueTaskSchedulerTest {

    private static final Duration LOCK_TTL = Duration.ofMinutes(4);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-19T00:00:00Z"),
            ZoneOffset.UTC
    );

    private TaskMapper taskMapper;
    private NotificationService notificationService;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        taskMapper = mock(TaskMapper.class);
        notificationService = mock(NotificationService.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void shouldUseFixedWindowAndStableEventKeyAcrossRuns() {
        when(valueOperations.setIfAbsent(
                eq("teamflow:lock:due-task-reminder"),
                anyString(),
                eq(LOCK_TTL)
        )).thenReturn(true);
        when(taskMapper.findDueBetween(
                anyString(),
                anyString(),
                eq(2),
                eq(0L)
        )).thenReturn(List.of(task("t001", "u001")));
        DueTaskScheduler scheduler = scheduler(2);

        scheduler.notifyDueTasks();
        scheduler.notifyDueTasks();

        verify(taskMapper, times(2)).findDueBetween(
                "2026-09-19T00:00:00.000Z",
                "2026-09-20T00:00:00.000Z",
                2,
                0L
        );
        ArgumentCaptor<CreateNotificationCommand> commands =
                ArgumentCaptor.forClass(CreateNotificationCommand.class);
        verify(notificationService, times(2)).create(commands.capture());
        assertEquals(
                "TASK_DUE_SOON:t001:u001:2026-09-19T12:00:00.000Z",
                commands.getAllValues().getFirst().eventKey()
        );
        assertEquals(
                commands.getAllValues().getFirst().eventKey(),
                commands.getAllValues().getLast().eventKey()
        );

        ArgumentCaptor<String> owners = ArgumentCaptor.forClass(String.class);
        verify(valueOperations, times(2)).setIfAbsent(
                eq("teamflow:lock:due-task-reminder"),
                owners.capture(),
                eq(LOCK_TTL)
        );
        assertNotEquals(
                owners.getAllValues().getFirst(),
                owners.getAllValues().getLast()
        );
        verify(redisTemplate, times(2)).execute(
                any(),
                eq(List.of("teamflow:lock:due-task-reminder")),
                anyString()
        );
    }

    @Test
    void shouldSkipRunWhenLeaseCannotBeAcquired() {
        when(valueOperations.setIfAbsent(
                anyString(),
                anyString(),
                any(Duration.class)
        )).thenReturn(false);

        scheduler(10).notifyDueTasks();

        verify(taskMapper, never()).findDueBetween(
                anyString(),
                anyString(),
                any(Integer.class),
                anyLong()
        );
        verify(redisTemplate, never()).execute(
                any(),
                any(List.class),
                any()
        );
    }

    @Test
    void shouldContinueAfterOneNotificationFails() {
        when(valueOperations.setIfAbsent(
                anyString(),
                anyString(),
                any(Duration.class)
        )).thenReturn(true);
        Task first = task("t001", "u001");
        Task second = task("t002", "u002");
        when(taskMapper.findDueBetween(
                anyString(),
                anyString(),
                eq(3),
                eq(0L)
        )).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("temporary failure"))
                .doReturn(null)
                .when(notificationService)
                .create(any(CreateNotificationCommand.class));

        scheduler(3).notifyDueTasks();

        verify(notificationService, times(2)).create(
                any(CreateNotificationCommand.class)
        );
        verify(redisTemplate).execute(
                any(),
                eq(List.of("teamflow:lock:due-task-reminder")),
                anyString()
        );
    }

    @Test
    void shouldCreateNewIdempotencyKeyAfterReassignment() {
        when(valueOperations.setIfAbsent(
                anyString(),
                anyString(),
                any(Duration.class)
        )).thenReturn(true);
        when(taskMapper.findDueBetween(
                anyString(),
                anyString(),
                eq(2),
                eq(0L)
        )).thenReturn(
                List.of(task("t001", "u001")),
                List.of(task("t001", "u002"))
        );
        DueTaskScheduler scheduler = scheduler(2);

        scheduler.notifyDueTasks();
        scheduler.notifyDueTasks();

        ArgumentCaptor<CreateNotificationCommand> commands =
                ArgumentCaptor.forClass(CreateNotificationCommand.class);
        verify(notificationService, times(2)).create(commands.capture());
        assertNotEquals(
                commands.getAllValues().getFirst().eventKey(),
                commands.getAllValues().getLast().eventKey()
        );
        assertEquals(
                "TASK_DUE_SOON:t001:u002:2026-09-19T12:00:00.000Z",
                commands.getAllValues().getLast().eventKey()
        );
    }

    @Test
    void shouldStopAtConfiguredPerRunBatchLimit() {
        when(valueOperations.setIfAbsent(
                anyString(),
                anyString(),
                any(Duration.class)
        )).thenReturn(true);
        when(taskMapper.findDueBetween(
                anyString(),
                anyString(),
                eq(1),
                anyLong()
        )).thenReturn(List.of(task("t001", null)));

        scheduler(1).notifyDueTasks();

        verify(taskMapper, times(DueTaskScheduler.MAX_BATCHES_PER_RUN))
                .findDueBetween(
                        anyString(),
                        anyString(),
                        eq(1),
                        anyLong()
                );
        verify(notificationService, never()).create(any());
    }

    private DueTaskScheduler scheduler(int batchSize) {
        return new DueTaskScheduler(
                taskMapper,
                notificationService,
                redisTemplate,
                new OperationsProperties(
                        Duration.ofHours(24),
                        batchSize,
                        LOCK_TTL,
                        Duration.ofHours(24),
                        4
                ),
                CLOCK
        );
    }

    private Task task(String id, String assigneeId) {
        return new Task(
                id,
                "p001",
                "Due task",
                null,
                TaskStatus.TODO,
                TaskPriority.MEDIUM,
                assigneeId,
                "u999",
                "2026-09-19T12:00:00.000Z",
                0,
                "2026-09-18T00:00:00.000Z",
                "2026-09-18T00:00:00.000Z"
        );
    }
}
