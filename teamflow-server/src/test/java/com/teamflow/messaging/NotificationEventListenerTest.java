package com.teamflow.messaging;

import com.teamflow.core.notification.domain.NotificationType;
import com.teamflow.core.notification.dto.CreateNotificationCommand;
import com.teamflow.core.notification.dto.NotificationResponse;
import com.teamflow.core.notification.service.NotificationService;
import com.teamflow.sse.SseConnectionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** RabbitMQ 消费者的幂等键、持久化与推送边界测试。 */
@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    private static final String NOW = "2026-09-18T01:02:03.456Z";

    @Mock
    private NotificationService notificationService;
    @Mock
    private SseConnectionRegistry sseRegistry;

    private NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new NotificationEventListener(
                notificationService,
                sseRegistry
        );
    }

    @Test
    void shouldUseSameIdempotencyKeyForDuplicateDelivery() {
        NotificationResponse response = response();
        when(notificationService.create(any())).thenReturn(response);

        listener.onAssignmentChanged(assignedEvent());
        listener.onAssignmentChanged(assignedEvent());

        ArgumentCaptor<CreateNotificationCommand> captor =
                ArgumentCaptor.forClass(CreateNotificationCommand.class);
        verify(notificationService,
                org.mockito.Mockito.times(2)).create(captor.capture());
        List<CreateNotificationCommand> commands = captor.getAllValues();
        assertEquals(commands.get(0).eventKey(), commands.get(1).eventKey());
        assertEquals(NotificationType.TASK_ASSIGNED, commands.get(0).type());
    }

    @Test
    void shouldIgnoreUnassignmentWithoutRecipient() {
        listener.onAssignmentChanged(new TaskAssignmentChangedEvent(
                "t001:2:ASSIGNEE_CHANGED",
                "t001",
                "p001",
                2,
                "u002",
                null,
                "u001",
                NOW
        ));

        verifyNoInteractions(notificationService, sseRegistry);
    }

    @Test
    void shouldAckPersistedNotificationWhenSsePushFails() {
        NotificationResponse response = response();
        when(notificationService.create(any())).thenReturn(response);
        doThrow(new IllegalStateException("broken connection"))
                .when(sseRegistry)
                .send("u002", "notification", response);

        assertDoesNotThrow(
                () -> listener.onAssignmentChanged(assignedEvent())
        );
    }

    @Test
    void shouldPropagatePersistenceFailureForContainerRetry() {
        when(notificationService.create(any()))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(
                IllegalStateException.class,
                () -> listener.onAssignmentChanged(assignedEvent())
        );
        verify(sseRegistry, never()).send(any(), any(), any());
    }

    private TaskAssignmentChangedEvent assignedEvent() {
        return new TaskAssignmentChangedEvent(
                "t001:1:ASSIGNEE_CHANGED",
                "t001",
                "p001",
                1,
                null,
                "u002",
                "u001",
                NOW
        );
    }

    private NotificationResponse response() {
        return new NotificationResponse(
                "n001",
                NotificationType.TASK_ASSIGNED,
                "你被指派了一个任务",
                "任务 t001 已指派给你",
                false,
                NOW
        );
    }
}
