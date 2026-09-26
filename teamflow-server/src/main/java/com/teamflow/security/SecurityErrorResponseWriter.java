package com.teamflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamflow.common.api.ApiResponse;
import com.teamflow.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 在 Spring MVC 控制器之外写出统一的 JSON 错误响应。
 *
 * <p>安全过滤器、认证入口和拒绝访问处理器都运行在控制器之前，无法交给
 * {@code @RestControllerAdvice} 统一处理，因此由本类集中维护响应格式。</p>
 */
@Component
public final class SecurityErrorResponseWriter {

    private final ObjectMapper objectMapper;

    /**
     * 创建安全错误响应写入器。
     *
     * @param objectMapper Spring Boot 统一配置的 JSON 转换器
     */
    public SecurityErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "JSON 转换器不能为 null"
        );
    }

    /**
     * 将业务错误码同时转换为 HTTP 状态和统一响应体。
     *
     * @param response Servlet 响应
     * @param errorCode 需要返回的业务错误码
     * @throws IOException 写入网络响应失败
     */
    public void write(HttpServletResponse response, ErrorCode errorCode)
            throws IOException {
        Objects.requireNonNull(response, "HTTP 响应不能为 null");
        Objects.requireNonNull(errorCode, "业务错误码不能为 null");

        response.setStatus(errorCode.httpStatus());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (errorCode.httpStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiResponse.failure(errorCode)
        );
    }
}
