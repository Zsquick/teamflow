package com.teamflow.core.outbox.domain;

/** Outbox 事件的持久化投递状态。 */
public enum OutboxEventStatus {
    PENDING,
    PROCESSING,
    RETRY,
    PUBLISHED,
    FAILED
}
