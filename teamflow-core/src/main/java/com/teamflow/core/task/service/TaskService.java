package com.teamflow.core.task.service;

import com.teamflow.common.api.PageQuery;
import com.teamflow.common.api.PageResult;
import com.teamflow.core.task.domain.TaskStatus;
import com.teamflow.core.task.dto.ChangeTaskStatusRequest;
import com.teamflow.core.task.dto.CreateTaskRequest;
import com.teamflow.core.task.dto.TaskResponse;
import com.teamflow.core.task.dto.UpdateTaskRequest;

/** 任务看板业务契约。 */
public interface TaskService {

    TaskResponse create(
            String currentUserId,
            CreateTaskRequest request
    );

    PageResult<TaskResponse> listByProject(
            String currentUserId,
            String projectId,
            TaskStatus status,
            PageQuery pageQuery
    );

    TaskResponse get(String currentUserId, String taskId);

    TaskResponse update(
            String currentUserId,
            String taskId,
            UpdateTaskRequest request
    );

    TaskResponse changeStatus(
            String currentUserId,
            String taskId,
            ChangeTaskStatusRequest request
    );

    void delete(
            String currentUserId,
            String taskId,
            int expectedVersion
    );
}
