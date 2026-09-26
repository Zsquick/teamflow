package com.teamflow.core.task.mapper;

import com.teamflow.common.validation.NumberValues;
import com.teamflow.core.task.domain.TaskStatus;

import java.util.Objects;

/** 某个任务状态的聚合数量。 */
public record TaskStatusCount(TaskStatus status, long count) {

    public TaskStatusCount {
        Objects.requireNonNull(status, "任务状态不能为 null");
        NumberValues.requireNonNegative(count, "任务数量");
    }
}
