package com.teamflow.sse;

import com.teamflow.security.AuthenticatedUser;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Objects;

/** 为当前认证用户建立通知 SSE 流。 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationStreamController {

    private final SseConnectionRegistry registry;

    public NotificationStreamController(SseConnectionRegistry registry) {
        this.registry = Objects.requireNonNull(
                registry,
                "SSE 连接注册表不能为 null"
        );
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .cacheControl(CacheControl.noCache())
                .header("X-Accel-Buffering", "no")
                .body(registry.connect(user.id()));
    }
}
