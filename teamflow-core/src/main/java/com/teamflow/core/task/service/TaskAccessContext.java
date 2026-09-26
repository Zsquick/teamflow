package com.teamflow.core.task.service;

import com.teamflow.core.project.domain.Project;
import com.teamflow.core.task.domain.Task;

import java.util.Objects;

/** 已通过任务访问校验的任务与所属项目上下文。 */
public record TaskAccessContext(Task task, Project project) {

    public TaskAccessContext {
        Objects.requireNonNull(task, "任务不能为 null");
        Objects.requireNonNull(project, "项目不能为 null");
        if (!task.getProjectId().equals(project.getId())) {
            throw new IllegalArgumentException("任务与项目访问上下文不匹配");
        }
    }
}
