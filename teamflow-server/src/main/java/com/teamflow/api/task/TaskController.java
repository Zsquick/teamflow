package com.teamflow.api.task;

import com.teamflow.audit.Audited;
import com.teamflow.common.api.ApiResponse;
import com.teamflow.common.api.PageQuery;
import com.teamflow.common.api.PageResult;
import com.teamflow.core.task.domain.TaskStatus;
import com.teamflow.core.task.dto.ChangeTaskStatusRequest;
import com.teamflow.core.task.dto.CreateTaskRequest;
import com.teamflow.core.task.dto.TaskResponse;
import com.teamflow.core.task.dto.UpdateTaskRequest;
import com.teamflow.core.task.service.TaskService;
import com.teamflow.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/** 登录团队成员使用的任务看板 REST 接口。 */
@Validated
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = Objects.requireNonNull(
                taskService,
                "任务服务不能为 null"
        );
    }

    @Audited(
            action = "TASK_CREATE",
            resourceType = "PROJECT",
            resourceId = "#request.projectId"
    )
    @PostMapping
    public ApiResponse<TaskResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateTaskRequest request
    ) {
        return ApiResponse.success(taskService.create(user.id(), request));
    }

    @GetMapping
    public ApiResponse<PageResult<TaskResponse>> listByProject(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam String projectId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20")
            @Min(1) @Max(PageQuery.MAX_SIZE) int size
    ) {
        return ApiResponse.success(
                taskService.listByProject(
                        user.id(),
                        projectId,
                        status,
                        new PageQuery(page, size)
                )
        );
    }

    @GetMapping("/{taskId}")
    public ApiResponse<TaskResponse> get(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String taskId
    ) {
        return ApiResponse.success(taskService.get(user.id(), taskId));
    }

    @Audited(
            action = "TASK_UPDATE",
            resourceType = "TASK",
            resourceId = "#taskId"
    )
    @PutMapping("/{taskId}")
    public ApiResponse<TaskResponse> update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String taskId,
            @Valid @RequestBody UpdateTaskRequest request
    ) {
        return ApiResponse.success(
                taskService.update(user.id(), taskId, request)
        );
    }

    @Audited(
            action = "TASK_STATUS_CHANGE",
            resourceType = "TASK",
            resourceId = "#taskId"
    )
    @PatchMapping("/{taskId}/status")
    public ApiResponse<TaskResponse> changeStatus(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String taskId,
            @Valid @RequestBody ChangeTaskStatusRequest request
    ) {
        return ApiResponse.success(
                taskService.changeStatus(user.id(), taskId, request)
        );
    }

    @Audited(
            action = "TASK_DELETE",
            resourceType = "TASK",
            resourceId = "#taskId"
    )
    @DeleteMapping("/{taskId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String taskId,
            @RequestParam("version") @PositiveOrZero int expectedVersion
    ) {
        taskService.delete(user.id(), taskId, expectedVersion);
        return ApiResponse.success(null);
    }
}
