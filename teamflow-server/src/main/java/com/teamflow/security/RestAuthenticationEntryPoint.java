package com.teamflow.security;

import com.teamflow.common.error.CommonErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Objects;

/**
 * 将“尚未认证却访问受保护资源”转换成统一 JSON 响应。
 */
@Component
public final class RestAuthenticationEntryPoint
        implements AuthenticationEntryPoint {

    private final SecurityErrorResponseWriter responseWriter;

    /**
     * 创建 REST 认证入口。
     *
     * @param responseWriter 安全错误响应写入器
     */
    public RestAuthenticationEntryPoint(
            SecurityErrorResponseWriter responseWriter
    ) {
        this.responseWriter = Objects.requireNonNull(
                responseWriter,
                "安全错误响应写入器不能为 null"
        );
    }

    /**
     * 返回 HTTP 401 和 {@code COMMON_0003}，提示调用方先登录。
     */
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException
    ) throws IOException, ServletException {
        responseWriter.write(response, CommonErrorCode.UNAUTHORIZED);
    }
}
