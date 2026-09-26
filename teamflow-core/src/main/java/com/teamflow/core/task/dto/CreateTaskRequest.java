package com.teamflow.core.task.dto;

import com.teamflow.core.common.validation.CanonicalUtcTime;
import com.teamflow.core.task.domain.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建任务请求。
 *
 * @param projectId 所属项目标识
 * @param title 任务标题
 * @param description 任务描述
 * @param priority 优先级
 * @param assigneeId 负责人标识，可为空
 * @param dueAt 截止时间，可为空
 */
public record CreateTaskRequest(
        @NotBlank(message = "项目编号不能为空")
        @Size(max = 32, message = "项目编号不能超过 32 个字符")
        @Pattern(regexp = "p[0-9]{3,}", message = "项目编号格式不正确")
        String projectId,
        @NotBlank(message = "任务标题不能为空")
        @Size(max = 200, message = "任务标题不能超过 200 个字符")
        String title,
        @Size(max = 5000, message = "任务描述不能超过 5000 个字符")
        String description,
        @NotNull(message = "任务优先级不能为空")
        TaskPriority priority,
        @Size(max = 32, message = "负责人编号不能超过 32 个字符")
        @Pattern(regexp = "u[0-9]{3,}", message = "负责人编号格式不正确")
        String assigneeId,
        @CanonicalUtcTime
        String dueAt
) {
}
