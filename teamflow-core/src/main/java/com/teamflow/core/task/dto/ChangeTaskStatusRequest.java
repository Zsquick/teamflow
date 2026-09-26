package com.teamflow.core.task.dto;

import com.teamflow.core.task.domain.TaskStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** 看板拖动任务时使用的最小状态变更命令。 */
public record ChangeTaskStatusRequest(
        @NotNull(message = "任务状态不能为空")
        TaskStatus status,
        @NotNull(message = "任务版本不能为空")
        @PositiveOrZero(message = "任务版本不能小于 0")
        Integer version
) {
}
