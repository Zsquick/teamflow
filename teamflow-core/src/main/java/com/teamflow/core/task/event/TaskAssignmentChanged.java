package com.teamflow.core.task.event;

import com.teamflow.common.validation.TextValues;
import com.teamflow.core.common.time.UtcTimeText;
import com.teamflow.core.common.version.Versions;

import java.util.Objects;

/** 任务负责人真实发生变化后产生的领域事件快照。 */
public record TaskAssignmentChanged(
        String taskId,
        String projectId,
        int taskVersion,
        String previousAssigneeId,
        String assigneeId,
        String operatorId,
        String occurredAt
) {

    public TaskAssignmentChanged {
        TextValues.requireNonBlank(taskId, "任务编号");
        TextValues.requireNonBlank(projectId, "项目编号");
        TextValues.requireNonBlank(operatorId, "操作者编号");
        Versions.requireNonNegative(taskVersion, "任务版本");
        TextValues.requireOptionalNonBlank(
                previousAssigneeId,
                "原负责人编号"
        );
        TextValues.requireOptionalNonBlank(
                assigneeId,
                "新负责人编号"
        );
        if (Objects.equals(previousAssigneeId, assigneeId)) {
            throw new IllegalArgumentException("负责人变化事件必须包含真实变化");
        }
        UtcTimeText.requireValid(occurredAt, "事件发生时间");
    }
}
