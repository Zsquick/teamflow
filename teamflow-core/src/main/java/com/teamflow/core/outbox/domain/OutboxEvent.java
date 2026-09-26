package com.teamflow.core.outbox.domain;

import com.teamflow.common.validation.TextValues;
import com.teamflow.core.common.time.UtcTimeText;
import com.teamflow.core.common.version.Versions;
import com.teamflow.core.task.event.TaskAssignmentChanged;

import java.util.Objects;

/** Outbox 中可重试发布的事件快照；旧任务列同时作为通用聚合列复用。 */
public record OutboxEvent(
        String id,
        String eventKey,
        String eventType,
        String taskId,
        String projectId,
        int taskVersion,
        String previousAssigneeId,
        String assigneeId,
        String operatorId,
        String occurredAt,
        OutboxEventStatus status,
        int attemptCount,
        String nextAttemptAt,
        String lockedBy,
        String lockedAt,
        String publishedAt,
        String lastError,
        String createdAt,
        String updatedAt,
        String eventPayload
) {

    public static final String TASK_ASSIGNEE_CHANGED =
            "TASK_ASSIGNEE_CHANGED";
    public static final String TEAM_INVITATION_CHANGED =
            "TEAM_INVITATION_CHANGED";

    public OutboxEvent {
        id = TextValues.requireNonBlank(id, "Outbox 事件编号");
        eventKey = TextValues.requireNonBlank(eventKey, "事件幂等键");
        eventType = TextValues.requireNonBlank(eventType, "事件类型");
        taskId = TextValues.requireNonBlank(taskId, "任务编号");
        projectId = TextValues.requireNonBlank(projectId, "项目编号");
        taskVersion = Versions.requireNonNegative(taskVersion, "任务版本");
        previousAssigneeId = TextValues.requireOptionalNonBlank(
                previousAssigneeId,
                "原负责人编号"
        );
        assigneeId = TextValues.requireOptionalNonBlank(
                assigneeId,
                "新负责人编号"
        );
        if (Objects.equals(previousAssigneeId, assigneeId)) {
            throw new IllegalArgumentException(
                    "负责人变化事件必须包含真实变化"
            );
        }
        operatorId = TextValues.requireNonBlank(operatorId, "操作者编号");
        occurredAt = UtcTimeText.requireValid(occurredAt, "事件发生时间");
        status = Objects.requireNonNull(status, "Outbox 状态不能为 null");
        if (attemptCount < 0) {
            throw new IllegalArgumentException("投递尝试次数不能为负数");
        }
        nextAttemptAt = UtcTimeText.requireValid(
                nextAttemptAt,
                "下次尝试时间"
        );
        lockedBy = TextValues.requireOptionalNonBlank(lockedBy, "领取者");
        lockedAt = requireOptionalTime(lockedAt, "领取时间");
        publishedAt = requireOptionalTime(publishedAt, "发布时间");
        lastError = TextValues.requireOptionalNonBlank(lastError, "最后错误");
        createdAt = UtcTimeText.requireValid(createdAt, "创建时间");
        updatedAt = UtcTimeText.requireValid(updatedAt, "更新时间");
        eventPayload = TextValues.requireOptionalNonBlank(
                eventPayload,
                "事件载荷"
        );
    }

    /** 保留任务事件原有构造方式，其载荷由结构化列表示。 */
    public OutboxEvent(
            String id,
            String eventKey,
            String eventType,
            String taskId,
            String projectId,
            int taskVersion,
            String previousAssigneeId,
            String assigneeId,
            String operatorId,
            String occurredAt,
            OutboxEventStatus status,
            int attemptCount,
            String nextAttemptAt,
            String lockedBy,
            String lockedAt,
            String publishedAt,
            String lastError,
            String createdAt,
            String updatedAt
    ) {
        this(id, eventKey, eventType, taskId, projectId, taskVersion,
                previousAssigneeId, assigneeId, operatorId, occurredAt,
                status, attemptCount, nextAttemptAt, lockedBy, lockedAt,
                publishedAt, lastError, createdAt, updatedAt, null);
    }

    /** 由领域事件创建尚未投递的 Outbox 记录。 */
    public static OutboxEvent pending(
            String id,
            TaskAssignmentChanged event
    ) {
        Objects.requireNonNull(event, "任务负责人变化事件不能为 null");
        String eventKey = event.taskId()
                + ":"
                + event.taskVersion()
                + ":ASSIGNEE_CHANGED";
        return new OutboxEvent(
                id,
                eventKey,
                TASK_ASSIGNEE_CHANGED,
                event.taskId(),
                event.projectId(),
                event.taskVersion(),
                event.previousAssigneeId(),
                event.assigneeId(),
                event.operatorId(),
                event.occurredAt(),
                OutboxEventStatus.PENDING,
                0,
                event.occurredAt(),
                null,
                null,
                null,
                null,
                event.occurredAt(),
                event.occurredAt(),
                null
        );
    }

    /** 为非任务类型创建携带 JSON 快照的 Outbox 记录。 */
    public static OutboxEvent pendingPayload(
            String id,
            String eventKey,
            String eventType,
            String aggregateId,
            String parentId,
            int aggregateVersion,
            String recipientId,
            String operatorId,
            String occurredAt,
            String eventPayload
    ) {
        return new OutboxEvent(
                id, eventKey, eventType, aggregateId, parentId,
                aggregateVersion, null, recipientId, operatorId,
                occurredAt, OutboxEventStatus.PENDING, 0, occurredAt,
                null, null, null, null, occurredAt, occurredAt,
                TextValues.requireNonBlank(eventPayload, "事件载荷")
        );
    }

    private static String requireOptionalTime(
            String value,
            String fieldName
    ) {
        return value == null
                ? null
                : UtcTimeText.requireValid(value, fieldName);
    }
}
