package com.teamflow.security;

import com.teamflow.core.auth.error.AuthErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * 从 Authorization Bearer 请求头恢复认证信息。
 */
@Component
public final class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final SecurityErrorResponseWriter responseWriter;

    /**
     * 创建 JWT 认证过滤器。
     *
     * @param jwtTokenService JWT 服务
     * @param responseWriter 安全错误响应写入器
     */
    public JwtAuthenticationFilter(
            JwtTokenService jwtTokenService,
            SecurityErrorResponseWriter responseWriter
    ) {
        this.jwtTokenService = Objects.requireNonNull(
                jwtTokenService,
                "JWT 服务不能为 null"
        );
        this.responseWriter = Objects.requireNonNull(
                responseWriter,
                "安全错误响应写入器不能为 null"
        );
    }

    /**
     * 解析 Bearer Token，并在验证成功后写入 SecurityContext。
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 处理失败
     * @throws IOException I/O 失败
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!hasBearerScheme(authorization)
                || SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring(BEARER_PREFIX.length());
        try {
            Claims claims = jwtTokenService.parse(token);
            AuthenticatedUser user = jwtTokenService.toAuthenticatedUser(claims);
            setAuthentication(request, user);

        } catch (ExpiredJwtException exception) {
            reject(response, AuthErrorCode.ACCESS_TOKEN_EXPIRED);
            return;
        } catch (JwtException | IllegalArgumentException exception) {
            reject(response, AuthErrorCode.ACCESS_TOKEN_INVALID);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean hasBearerScheme(String authorization) {
        return authorization != null
                && authorization.regionMatches(
                        true,
                        0,
                        BEARER_PREFIX,
                        0,
                        BEARER_PREFIX.length()
                );
    }

    private static void setAuthentication(
            HttpServletRequest request,
            AuthenticatedUser user
    ) {
        List<SimpleGrantedAuthority> authorities = user.authorities().stream()
                .sorted()
                .map(SimpleGrantedAuthority::new)
                .toList();
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        user,
                        null,
                        authorities
                );
        authentication.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request)
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    private void reject(
            HttpServletResponse response,
            AuthErrorCode errorCode
    ) throws IOException {
        SecurityContextHolder.clearContext();
        responseWriter.write(response, errorCode);
    }
}
