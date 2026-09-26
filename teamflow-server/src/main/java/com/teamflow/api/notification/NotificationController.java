package com.teamflow.api.notification;

import com.teamflow.audit.Audited;
import com.teamflow.common.api.ApiResponse;
import com.teamflow.common.api.PageQuery;
import com.teamflow.common.api.PageResult;
import com.teamflow.core.notification.dto.NotificationResponse;
import com.teamflow.core.notification.service.NotificationService;
import com.teamflow.security.AuthenticatedUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/** 当前登录用户的站内通知 REST 接口。 */
@Validated
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = Objects.requireNonNull(
                notificationService,
                "通知服务不能为 null"
        );
    }

    @GetMapping
    public ApiResponse<PageResult<NotificationResponse>> listMine(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20")
            @Min(1) @Max(PageQuery.MAX_SIZE) int size
    ) {
        return ApiResponse.success(notificationService.listMine(
                user.id(),
                new PageQuery(page, size)
        ));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> countUnread(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(
                notificationService.countUnread(user.id())
        );
    }

    @Audited(
            action = "NOTIFICATION_MARK_READ",
            resourceType = "NOTIFICATION",
            resourceId = "#notificationId"
    )
    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> markRead(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String notificationId
    ) {
        notificationService.markRead(user.id(), notificationId);
        return ApiResponse.success(null);
    }
}
