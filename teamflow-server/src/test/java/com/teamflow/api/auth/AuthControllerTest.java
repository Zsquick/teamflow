package com.teamflow.api.auth;

import com.teamflow.common.api.ApiResponse;
import com.teamflow.core.auth.dto.LoginRequest;
import com.teamflow.core.auth.dto.RefreshTokenRequest;
import com.teamflow.core.auth.dto.RegisterRequest;
import com.teamflow.core.auth.dto.TokenResponse;
import com.teamflow.core.auth.service.AuthService;
import com.teamflow.core.user.domain.UserStatus;
import com.teamflow.core.user.dto.UserResponse;
import com.teamflow.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 认证 HTTP 接口的委托与响应包装测试。 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authService);
    }

    @Test
    void shouldRequireAuthService() {
        assertThrows(
                NullPointerException.class,
                () -> new AuthController(null)
        );
    }

    @Test
    void shouldRegisterAndWrapSuccessfulResponse() {
        RegisterRequest request = new RegisterRequest(
                "zhou",
                "zhou@example.com",
                "password",
                "小周"
        );
        UserResponse user = new UserResponse(
                "u001",
                "zhou",
                "zhou@example.com",
                "小周",
                null,
                UserStatus.ACTIVE
        );
        when(authService.register(request)).thenReturn(user);

        ApiResponse<UserResponse> response = controller.register(request);

        assertEquals("COMMON_0000", response.code());
        assertEquals(user, response.data());
        verify(authService).register(request);
    }

    @Test
    void shouldLoginAndWrapTokenResponse() {
        LoginRequest request = new LoginRequest("zhou", "password");
        TokenResponse tokens = TokenResponse.bearer("access", "refresh", 900);
        when(authService.login(request)).thenReturn(tokens);

        ApiResponse<TokenResponse> response = controller.login(request);

        assertEquals("COMMON_0000", response.code());
        assertEquals(tokens, response.data());
        verify(authService).login(request);
    }

    @Test
    void shouldRefreshAndWrapTokenResponse() {
        RefreshTokenRequest request = new RefreshTokenRequest("old-refresh");
        TokenResponse tokens = TokenResponse.bearer(
                "new-access",
                "new-refresh",
                900
        );
        when(authService.refresh(request)).thenReturn(tokens);

        ApiResponse<TokenResponse> response = controller.refresh(request);

        assertEquals("COMMON_0000", response.code());
        assertEquals(tokens, response.data());
        verify(authService).refresh(request);
    }

    @Test
    void shouldLogoutCurrentUserAndReturnEmptySuccessData() {
        AuthenticatedUser user = new AuthenticatedUser(
                "u001",
                "zhou",
                Set.of("ROLE_USER")
        );
        RefreshTokenRequest request = new RefreshTokenRequest("refresh-token");

        ApiResponse<Void> response = controller.logout(user, request);

        assertEquals("COMMON_0000", response.code());
        assertNull(response.data());
        verify(authService).logout("u001", "refresh-token");
    }
}
