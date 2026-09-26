package com.teamflow.scheduling;

import com.teamflow.core.outbox.domain.OutboxEvent;
import com.teamflow.core.outbox.domain.OutboxEventStatus;
import com.teamflow.core.outbox.service.OutboxEventService;
import com.teamflow.messaging.RabbitTaskEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Outbox 并发领取、发布结果与有限退避测试。 */
class OutboxDispatcherTest {

    private static final String NOW = "2026-09-19T00:00:00.000Z";
    private static final String STALE_BEFORE =
            "2026-09-18T23:59:30.000Z";
    private static final OutboxProperties PROPERTIES = new OutboxProperties(
            50,
            5,
            Duration.ofSeconds(30),
            Duration.ofSeconds(5),
            Duration.ofSeconds(2),
            Duration.ofMinutes(1)
    );

    private OutboxEventService outboxService;
    private RabbitTaskEventPublisher rabbitPublisher;
    private OutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        outboxService = mock(OutboxEventService.class);
        rabbitPublisher = mock(RabbitTaskEventPublisher.class);
        dispatcher = new OutboxDispatcher(
                outboxService,
                rabbitPublisher,
                PROPERTIES,
                Clock.fixed(Instant.parse(NOW), ZoneOffset.UTC)
        );
    }

    @Test
    void shouldPublishClaimedEventAndMarkItPublished() {
        OutboxEvent event = event(0);
        when(outboxService.findDispatchCandidates(
                NOW, STALE_BEFORE, 5, 50
        )).thenReturn(List.of(event));
        when(outboxService.claim(
                eq("oe001"), anyString(), eq(NOW), eq(STALE_BEFORE), eq(5)
        )).thenReturn(true);
        when(outboxService.markPublished(
                eq("oe001"), anyString(), eq(NOW)
        )).thenReturn(true);

        dispatcher.dispatch();

        verify(rabbitPublisher).publishAndConfirm(
                event,
                Duration.ofSeconds(5)
        );
        verify(outboxService).markPublished(
                eq("oe001"),
                anyString(),
                eq(NOW)
        );
    }

    @Test
    void shouldLeaveClaimedEventToWinningWorkerOnly() {
        OutboxEvent event = event(0);
        when(outboxService.findDispatchCandidates(
                NOW, STALE_BEFORE, 5, 50
        )).thenReturn(List.of(event));
        when(outboxService.claim(
                eq("oe001"), anyString(), eq(NOW), eq(STALE_BEFORE), eq(5)
        )).thenReturn(false);

        dispatcher.dispatch();

        verify(rabbitPublisher, never()).publishAndConfirm(
                eq(event),
                eq(Duration.ofSeconds(5))
        );
    }

    @Test
    void shouldRecordRetryWithExponentialDelay() {
        OutboxEvent event = event(1);
        prepareClaim(event);
        doThrow(new IllegalStateException("broker unavailable"))
                .when(rabbitPublisher)
                .publishAndConfirm(event, Duration.ofSeconds(5));
        when(outboxService.markRetry(
                eq("oe001"),
                anyString(),
                eq(2),
                eq("2026-09-19T00:00:04.000Z"),
                anyString(),
                eq(NOW)
        )).thenReturn(true);

        dispatcher.dispatch();

        verify(outboxService).markRetry(
                eq("oe001"),
                anyString(),
                eq(2),
                eq("2026-09-19T00:00:04.000Z"),
                eq("IllegalStateException: broker unavailable"),
                eq(NOW)
        );
        verify(outboxService, never()).markFailed(
                eq("oe001"),
                anyString(),
                eq(2),
                anyString(),
                eq(NOW)
        );
    }

    @Test
    void shouldStopRetryingAfterConfiguredMaximum() {
        OutboxEvent event = event(4);
        prepareClaim(event);
        doThrow(new IllegalStateException("broker unavailable"))
                .when(rabbitPublisher)
                .publishAndConfirm(event, Duration.ofSeconds(5));
        when(outboxService.markFailed(
                eq("oe001"), anyString(), eq(5), anyString(), eq(NOW)
        )).thenReturn(true);

        dispatcher.dispatch();

        verify(outboxService).markFailed(
                eq("oe001"),
                anyString(),
                eq(5),
                eq("IllegalStateException: broker unavailable"),
                eq(NOW)
        );
        verify(outboxService, never()).markRetry(
                eq("oe001"),
                anyString(),
                eq(5),
                anyString(),
                anyString(),
                eq(NOW)
        );
    }

    @Test
    void shouldFailExhaustedStaleClaimsBeforeSelectingWork() {
        when(outboxService.findDispatchCandidates(
                NOW, STALE_BEFORE, 5, 50
        )).thenReturn(List.of());

        dispatcher.dispatch();

        verify(outboxService).markExpiredClaimsFailed(
                STALE_BEFORE,
                5,
                "发布工作者在最后一次领取后未回写结果",
                NOW
        );
    }

    private void prepareClaim(OutboxEvent event) {
        when(outboxService.findDispatchCandidates(
                NOW, STALE_BEFORE, 5, 50
        )).thenReturn(List.of(event));
        when(outboxService.claim(
                eq("oe001"), anyString(), eq(NOW), eq(STALE_BEFORE), eq(5)
        )).thenReturn(true);
    }

    private static OutboxEvent event(int attemptCount) {
        return new OutboxEvent(
                "oe001",
                "t001:4:ASSIGNEE_CHANGED",
                OutboxEvent.TASK_ASSIGNEE_CHANGED,
                "t001",
                "p001",
                4,
                "u002",
                "u003",
                "u001",
                "2026-09-18T01:02:03.456Z",
                attemptCount == 0
                        ? OutboxEventStatus.PENDING
                        : OutboxEventStatus.RETRY,
                attemptCount,
                "2026-09-18T02:00:00.000Z",
                null,
                null,
                null,
                attemptCount == 0 ? null : "previous failure",
                "2026-09-18T01:02:03.456Z",
                "2026-09-18T01:02:03.456Z"
        );
    }
}
