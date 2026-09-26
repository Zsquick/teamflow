package com.teamflow.security;

import com.teamflow.common.error.CommonErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Objects;

/**
 * 将“已经认证但权限不足”转换成统一 JSON 响应。
 */
@Component
public final class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityErrorResponseWriter responseWriter;

    /**
     * 创建 REST 拒绝访问处理器。
     *
     * @param responseWriter 安全错误响应写入器
     */
    public RestAccessDeniedHandler(SecurityErrorResponseWriter responseWriter) {
        this.responseWriter = Objects.requireNonNull(
                responseWriter,
                "安全错误响应写入器不能为 null"
        );
    }

    /**
     * 返回 HTTP 403 和 {@code COMMON_0004}，表示身份存在但权限不足。
     */
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException, ServletException {
        responseWriter.write(response, CommonErrorCode.FORBIDDEN);
    }
}
