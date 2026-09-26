package com.teamflow.scheduling;

import com.teamflow.core.common.time.UtcTimeText;
import com.teamflow.core.outbox.domain.OutboxEvent;
import com.teamflow.core.outbox.service.OutboxEventService;
import com.teamflow.messaging.RabbitTaskEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 有界扫描 Outbox，确认发布后再回写最终状态。 */
@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(
            OutboxDispatcher.class
    );
    private static final int MAX_ERROR_LENGTH = 1000;
    private static final String EXPIRED_CLAIM_ERROR =
            "发布工作者在最后一次领取后未回写结果";

    private final OutboxEventService outboxService;
    private final RabbitTaskEventPublisher rabbitPublisher;
    private final OutboxProperties properties;
    private final Clock clock;

    public OutboxDispatcher(
            OutboxEventService outboxService,
            RabbitTaskEventPublisher rabbitPublisher,
            OutboxProperties properties,
            Clock clock
    ) {
        this.outboxService = Objects.requireNonNull(
                outboxService,
                "Outbox 服务不能为 null"
        );
        this.rabbitPublisher = Objects.requireNonNull(
                rabbitPublisher,
                "RabbitMQ 事件发布器不能为 null"
        );
        this.properties = Objects.requireNonNull(
                properties,
                "Outbox 配置不能为 null"
        );
        this.clock = Objects.requireNonNull(clock, "时钟不能为 null");
    }

    @Scheduled(
            fixedDelayString = "${teamflow.outbox.scan-interval:PT2S}",
            scheduler = "outboxTaskScheduler"
    )
    public void dispatch() {
        Instant scanTime = clock.instant();
        String now = UtcTimeText.format(scanTime);
        String staleBefore = UtcTimeText.format(
                scanTime.minus(properties.claimTtl())
        );
        int expired = outboxService.markExpiredClaimsFailed(
                staleBefore,
                properties.maxAttempts(),
                EXPIRED_CLAIM_ERROR,
                now
        );
        if (expired > 0) {
            log.error(
                    "Outbox 最终领取已超时，已标记为失败 count={}",
                    expired
            );
        }

        List<OutboxEvent> candidates = outboxService
                .findDispatchCandidates(
                        now,
                        staleBefore,
                        properties.maxAttempts(),
                        properties.batchSize()
                );
        String workerId = UUID.randomUUID().toString();
        for (OutboxEvent event : candidates) {
            dispatchOne(event, workerId);
        }
    }

    private void dispatchOne(
            OutboxEvent event,
            String workerId
    ) {
        Instant claimTime = clock.instant();
        String claimedAt = UtcTimeText.format(claimTime);
        String staleBefore = UtcTimeText.format(
                claimTime.minus(properties.claimTtl())
        );
        if (!outboxService.claim(
                event.id(),
                workerId,
                claimedAt,
                staleBefore,
                properties.maxAttempts()
        )) {
            return;
        }

        int attemptCount = event.attemptCount() + 1;
        try {
            rabbitPublisher.publishAndConfirm(
                    event,
                    properties.confirmTimeout()
            );
            String publishedAt = UtcTimeText.now(clock);
            if (!outboxService.markPublished(
                    event.id(),
                    workerId,
                    publishedAt
            )) {
                log.error(
                        "Outbox 发布成功但状态回写失败 eventId={} eventKey={}",
                        event.id(),
                        event.eventKey()
                );
            }
        } catch (RuntimeException exception) {
            recordFailure(event, workerId, attemptCount, exception);
        }
    }

    private void recordFailure(
            OutboxEvent event,
            String workerId,
            int attemptCount,
            RuntimeException exception
    ) {
        Instant failedAt = clock.instant();
        String failedAtText = UtcTimeText.format(failedAt);
        String error = errorText(exception);
        boolean finalFailure = attemptCount >= properties.maxAttempts();
        boolean updated;
        if (finalFailure) {
            updated = outboxService.markFailed(
                    event.id(),
                    workerId,
                    attemptCount,
                    error,
                    failedAtText
            );
        } else {
            String nextAttemptAt = UtcTimeText.format(
                    failedAt.plus(retryDelay(attemptCount))
            );
            updated = outboxService.markRetry(
                    event.id(),
                    workerId,
                    attemptCount,
                    nextAttemptAt,
                    error,
                    failedAtText
            );
        }
        log.atWarn()
                .setCause(exception)
                .log(
                        "Outbox 发布失败 eventId={} eventKey={} attempt={} finalFailure={} stateUpdated={}",
                        event.id(),
                        event.eventKey(),
                        attemptCount,
                        finalFailure,
                        updated
                );
    }

    private Duration retryDelay(int attemptCount) {
        Duration delay = properties.retryInitialDelay();
        for (int index = 1; index < attemptCount; index++) {
            if (delay.compareTo(properties.retryMaxDelay()) >= 0) {
                return properties.retryMaxDelay();
            }
            try {
                delay = delay.multipliedBy(2);
            } catch (ArithmeticException exception) {
                return properties.retryMaxDelay();
            }
        }
        return delay.compareTo(properties.retryMaxDelay()) > 0
                ? properties.retryMaxDelay()
                : delay;
    }

    private static String errorText(RuntimeException exception) {
        String message = exception.getClass().getSimpleName()
                + ": "
                + Objects.toString(exception.getMessage(), "无详细信息");
        return message.length() <= MAX_ERROR_LENGTH
                ? message
                : message.substring(0, MAX_ERROR_LENGTH);
    }
}
