package com.teamflow.api.comment;

import com.teamflow.audit.Audited;
import com.teamflow.common.api.ApiResponse;
import com.teamflow.core.comment.dto.CommentResponse;
import com.teamflow.core.comment.dto.CreateCommentRequest;
import com.teamflow.core.comment.service.TaskCommentService;
import com.teamflow.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/** 登录团队成员使用的任务评论 REST 接口。 */
@RestController
@RequestMapping("/api")
public class TaskCommentController {

    private final TaskCommentService commentService;

    public TaskCommentController(TaskCommentService commentService) {
        this.commentService = Objects.requireNonNull(
                commentService,
                "评论服务不能为 null"
        );
    }

    @Audited(
            action = "COMMENT_CREATE",
            resourceType = "TASK",
            resourceId = "#taskId"
    )
    @PostMapping("/tasks/{taskId}/comments")
    public ApiResponse<CommentResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String taskId,
            @Valid @RequestBody CreateCommentRequest request
    ) {
        return ApiResponse.success(
                commentService.create(user.id(), taskId, request)
        );
    }

    @GetMapping("/tasks/{taskId}/comments")
    public ApiResponse<List<CommentResponse>> listByTask(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String taskId
    ) {
        return ApiResponse.success(
                commentService.listByTask(user.id(), taskId)
        );
    }

    @Audited(
            action = "COMMENT_DELETE",
            resourceType = "TASK_COMMENT",
            resourceId = "#commentId"
    )
    @DeleteMapping("/comments/{commentId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String commentId
    ) {
        commentService.delete(user.id(), commentId);
        return ApiResponse.success(null);
    }
}
