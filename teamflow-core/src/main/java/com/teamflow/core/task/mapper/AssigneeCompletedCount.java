package com.teamflow.core.task.mapper;

import com.teamflow.common.validation.NumberValues;
import com.teamflow.common.validation.TextValues;

/** 某个负责人的已完成任务数量。 */
public record AssigneeCompletedCount(String assigneeId, long count) {

    public AssigneeCompletedCount {
        TextValues.requireNonBlank(assigneeId, "负责人编号");
        NumberValues.requireNonNegative(count, "已完成任务数量");
    }
}
