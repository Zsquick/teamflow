package com.teamflow.core.outbox.service;

import com.teamflow.core.common.id.ReadableIdGenerator;
import com.teamflow.core.common.id.ResourceType;
import com.teamflow.core.outbox.domain.OutboxEvent;
import com.teamflow.core.outbox.domain.OutboxEventStatus;
import com.teamflow.core.outbox.mapper.OutboxEventMapper;
import com.teamflow.core.task.event.TaskAssignmentChanged;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 领域事件原子转换为 Outbox 记录的测试。 */
class OutboxTaskEventPublisherTest {

    private final OutboxEventMapper mapper = mock(OutboxEventMapper.class);
    private final ReadableIdGenerator idGenerator =
            mock(ReadableIdGenerator.class);
    private final OutboxTaskEventPublisher publisher =
            new OutboxTaskEventPublisher(mapper, idGenerator);

    @Test
    void shouldPersistStablePendingEvent() {
        when(idGenerator.nextId(ResourceType.OUTBOX_EVENT))
                .thenReturn("oe001");
        when(mapper.insert(org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);

        publisher.publishAssignmentChanged(domainEvent());

        ArgumentCaptor<OutboxEvent> captor =
                ArgumentCaptor.forClass(OutboxEvent.class);
        verify(mapper).insert(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertAll(
                () -> assertEquals("oe001", saved.id()),
                () -> assertEquals(
                        "t001:4:ASSIGNEE_CHANGED",
                        saved.eventKey()
                ),
                () -> assertEquals(
                        OutboxEvent.TASK_ASSIGNEE_CHANGED,
                        saved.eventType()
                ),
                () -> assertEquals(OutboxEventStatus.PENDING, saved.status()),
                () -> assertEquals(0, saved.attemptCount()),
                () -> assertEquals(saved.occurredAt(), saved.nextAttemptAt())
        );
    }

    @Test
    void shouldFailBusinessCallWhenOutboxInsertIsNotExactlyOneRow() {
        when(idGenerator.nextId(ResourceType.OUTBOX_EVENT))
                .thenReturn("oe001");
        when(mapper.insert(org.mockito.ArgumentMatchers.any()))
                .thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> publisher.publishAssignmentChanged(domainEvent())
        );
    }

    private static TaskAssignmentChanged domainEvent() {
        return new TaskAssignmentChanged(
                "t001",
                "p001",
                4,
                "u002",
                "u003",
                "u001",
                "2026-09-18T01:02:03.456Z"
        );
    }
}
