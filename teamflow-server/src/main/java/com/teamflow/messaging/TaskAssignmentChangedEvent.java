package com.teamflow.messaging;

import com.teamflow.common.validation.TextValues;
import com.teamflow.core.common.time.UtcTimeText;
import com.teamflow.core.common.version.Versions;

import java.util.Objects;

/** RabbitMQ 中传输的任务指派事件。 */
public record TaskAssignmentChangedEvent(
        String eventId,
        String taskId,
        String projectId,
        int taskVersion,
        String previousAssigneeId,
        String assigneeId,
        String operatorId,
        String occurredAt
) {

    private static final int MAX_EVENT_ID_LENGTH = 128;

    public TaskAssignmentChangedEvent {
        eventId = requireEventId(eventId);
        taskId = TextValues.requireNonBlank(taskId, "任务编号");
        projectId = TextValues.requireNonBlank(projectId, "项目编号");
        taskVersion = Versions.requireNonNegative(
                taskVersion,
                "任务版本"
        );
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
        occurredAt = UtcTimeText.requireValid(
                occurredAt,
                "事件发生时间"
        );
    }

    private static String requireEventId(String eventId) {
        String value = TextValues.requireNonBlank(eventId, "事件编号");
        if (value.length() > MAX_EVENT_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "事件编号不能超过 " + MAX_EVENT_ID_LENGTH + " 个字符"
            );
        }
        return value;
    }
}
