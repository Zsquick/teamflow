package com.teamflow.core.task.dto;

import com.teamflow.core.common.validation.CanonicalUtcTime;
import com.teamflow.core.task.domain.TaskPriority;
import com.teamflow.core.task.domain.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 修改任务请求。
 *
 * @param title 任务标题
 * @param description 任务描述
 * @param status 任务状态
 * @param priority 优先级
 * @param assigneeId 负责人标识，可为空
 * @param dueAt 截止时间，可为空
 * @param version 当前乐观锁版本
 */
public record UpdateTaskRequest(
        @NotBlank(message = "任务标题不能为空")
        @Size(max = 200, message = "任务标题不能超过 200 个字符")
        String title,
        @Size(max = 5000, message = "任务描述不能超过 5000 个字符")
        String description,
        @NotNull(message = "任务状态不能为空")
        TaskStatus status,
        @NotNull(message = "任务优先级不能为空")
        TaskPriority priority,
        @Size(max = 32, message = "负责人编号不能超过 32 个字符")
        @Pattern(regexp = "u[0-9]{3,}", message = "负责人编号格式不正确")
        String assigneeId,
        @CanonicalUtcTime
        String dueAt,
        @NotNull(message = "任务版本不能为空")
        @PositiveOrZero(message = "任务版本不能小于 0")
        Integer version
) {
}
