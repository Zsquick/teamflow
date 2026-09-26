package com.teamflow.api.auth;

import com.teamflow.common.api.ApiResponse;
import com.teamflow.core.auth.dto.LoginRequest;
import com.teamflow.core.auth.dto.RefreshTokenRequest;
import com.teamflow.core.auth.dto.RegisterRequest;
import com.teamflow.core.auth.dto.TokenResponse;
import com.teamflow.core.auth.service.AuthService;
import com.teamflow.core.user.dto.UserResponse;
import com.teamflow.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * 接收认证相关 HTTP 请求，并把 Web 层参数转交给认证业务。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = Objects.requireNonNull(
                authService,
                "认证服务不能为 null"
        );
    }

    @Operation(
            summary = "注册账号",
            description = "公开接口，不需要 Bearer token。",
            security = {}
    )
    @SecurityRequirements
    @PostMapping("/register")
    public ApiResponse<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @Operation(
            summary = "账号登录",
            description = "公开接口，验证凭据后签发访问令牌。",
            security = {}
    )
    @SecurityRequirements
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @Operation(
            summary = "刷新访问令牌",
            description = "公开接口，使用请求正文中的刷新令牌换取新令牌。",
            security = {}
    )
    @SecurityRequirements
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal AuthenticatedUser user,
                                    @Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(user.id(), request.refreshToken());
        return ApiResponse.success(null);
    }
}
