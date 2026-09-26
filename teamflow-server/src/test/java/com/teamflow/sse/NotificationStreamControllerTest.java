package com.teamflow.sse;

import com.teamflow.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SSE 端点只使用认证主体，并返回禁止代理缓冲的流响应。 */
class NotificationStreamControllerTest {

    @Test
    void shouldOpenStreamForAuthenticatedUser() {
        SseConnectionRegistry registry = mock(SseConnectionRegistry.class);
        SseEmitter emitter = new SseEmitter();
        when(registry.connect("u001")).thenReturn(emitter);
        NotificationStreamController controller =
                new NotificationStreamController(registry);
        AuthenticatedUser user = new AuthenticatedUser(
                "u001",
                "zhou",
                Set.of("ROLE_USER")
        );

        ResponseEntity<SseEmitter> response = controller.stream(user);

        assertAll(
                () -> assertEquals(200, response.getStatusCode().value()),
                () -> assertEquals(
                        MediaType.TEXT_EVENT_STREAM,
                        response.getHeaders().getContentType()
                ),
                () -> assertEquals(
                        "no",
                        response.getHeaders().getFirst("X-Accel-Buffering")
                ),
                () -> assertSame(emitter, response.getBody())
        );
        verify(registry).connect("u001");
    }
}
