package com.teamflow.core.task.dto;

import com.teamflow.core.task.domain.TaskPriority;
import com.teamflow.core.task.domain.TaskStatus;
import com.teamflow.core.task.domain.Task;

import java.util.Objects;

/**
 * 任务响应。
 *
 * @param id 任务标识
 * @param projectId 项目标识
 * @param title 标题
 * @param description 描述
 * @param status 状态
 * @param priority 优先级
 * @param assigneeId 负责人标识
 * @param reporterId 创建者标识
 * @param dueAt 截止时间
 * @param version 乐观锁版本
 * @param createdAt 创建时间
 * @param updatedAt 修改时间
 */
public record TaskResponse(
        String id,
        String projectId,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        String assigneeId,
        String reporterId,
        String dueAt,
        int version,
        String createdAt,
        String updatedAt
) {

    /** 将任务领域对象转换为只读接口响应。 */
    public static TaskResponse from(Task task) {
        Objects.requireNonNull(task, "任务不能为 null");
        return new TaskResponse(
                task.getId(),
                task.getProjectId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getAssigneeId(),
                task.getReporterId(),
                task.getDueAt(),
                task.getVersion(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
