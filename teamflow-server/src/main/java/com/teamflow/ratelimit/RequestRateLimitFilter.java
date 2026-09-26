package com.teamflow.ratelimit;

import com.teamflow.security.AuthenticatedUser;
import com.teamflow.security.SecurityErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

/** 在 multipart 解析前限制认证入口和附件上传请求。 */
public final class RequestRateLimitFilter extends OncePerRequestFilter {

    private final RequestRateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final SecurityErrorResponseWriter responseWriter;

    public RequestRateLimitFilter(
            RequestRateLimiter rateLimiter,
            RateLimitProperties properties,
            SecurityErrorResponseWriter responseWriter
    ) {
        this.rateLimiter = Objects.requireNonNull(
                rateLimiter,
                "请求限流器不能为 null"
        );
        this.properties = Objects.requireNonNull(
                properties,
                "请求限流配置不能为 null"
        );
        this.responseWriter = Objects.requireNonNull(
                responseWriter,
                "错误响应写入器不能为 null"
        );
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!properties.enabled()
                || !HttpMethod.POST.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            checkRequest(request);
        } catch (RateLimitExceededException exception) {
            response.setHeader(
                    HttpHeaders.RETRY_AFTER,
                    Long.toString(exception.retryAfterSeconds())
            );
            responseWriter.write(response, exception.getErrorCode());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void checkRequest(HttpServletRequest request) {
        String path = request.getRequestURI().substring(
                request.getContextPath().length()
        );
        String clientAddress = Objects.requireNonNullElse(
                request.getRemoteAddr(),
                "unknown"
        );
        switch (path) {
            case "/api/auth/login" -> rateLimiter.check(
                    "login",
                    clientAddress,
                    properties.login()
            );
            case "/api/auth/register" -> rateLimiter.check(
                    "registration",
                    clientAddress,
                    properties.registration()
            );
            case "/api/auth/refresh" -> rateLimiter.check(
                    "refresh",
                    clientAddress,
                    properties.refresh()
            );
            default -> {
                if (isAttachmentUpload(path)) {
                    rateLimiter.check(
                            "upload",
                            currentUserId(clientAddress),
                            properties.upload()
                    );
                }
            }
        }
    }

    private static boolean isAttachmentUpload(String path) {
        String prefix = "/api/tasks/";
        String suffix = "/attachments";
        if (!path.startsWith(prefix) || !path.endsWith(suffix)) {
            return false;
        }
        String taskId = path.substring(
                prefix.length(),
                path.length() - suffix.length()
        );
        return !taskId.isBlank() && !taskId.contains("/");
    }

    private static String currentUserId(String fallback) {
        Authentication authentication = SecurityContextHolder.getContext()
                .getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.id();
        }
        return fallback;
    }
}
