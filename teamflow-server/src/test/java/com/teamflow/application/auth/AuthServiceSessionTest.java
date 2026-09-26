package com.teamflow.application.auth;

import com.teamflow.common.error.BusinessException;
import com.teamflow.core.auth.dto.LoginRequest;
import com.teamflow.core.auth.dto.RefreshTokenRequest;
import com.teamflow.core.auth.dto.TokenResponse;
import com.teamflow.core.auth.error.AuthErrorCode;
import com.teamflow.core.common.id.ReadableIdGenerator;
import com.teamflow.core.user.domain.User;
import com.teamflow.core.user.domain.UserStatus;
import com.teamflow.core.user.mapper.UserMapper;
import com.teamflow.security.AuthenticatedUser;
import com.teamflow.security.AuthenticationFailureTranslator;
import com.teamflow.security.JwtProperties;
import com.teamflow.security.JwtTokenService;
import com.teamflow.security.ParsedRefreshToken;
import com.teamflow.security.RefreshTokenStore;
import com.teamflow.security.TeamFlowUserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 登录、刷新与注销会话流程测试。 */
@ExtendWith(MockitoExtension.class)
class AuthServiceSessionTest {

    private static final String NOW = "2026-09-10T01:02:03.456Z";
    private static final String CURRENT_REFRESH_TOKEN = "refresh-current";

    @Mock
    private UserMapper userMapper;
    @Mock
    private ReadableIdGenerator idGenerator;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private RefreshTokenStore refreshTokenStore;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private AuthenticationFailureTranslator failureTranslator;
    @Mock
    private Claims claims;

    private JwtProperties jwtProperties;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties(
                "teamflow",
                Duration.ofMinutes(15),
                Duration.ofDays(7),
                Base64.getEncoder().encodeToString(
                        "0123456789abcdef0123456789abcdef"
                                .getBytes(StandardCharsets.UTF_8)
                )
        );
        authService = new AuthServiceImpl(
                userMapper,
                idGenerator,
                passwordEncoder,
                Clock.fixed(Instant.parse(NOW), ZoneOffset.UTC),
                jwtTokenService,
                refreshTokenStore,
                jwtProperties,
                authenticationManager,
                failureTranslator
        );
    }

    @Test
    void shouldAuthenticateAndSaveOneDeviceRefreshSession() {
        TeamFlowUserDetails details = TeamFlowUserDetails.from(
                user(UserStatus.ACTIVE, "zhou")
        );
        Authentication authenticationResult =
                UsernamePasswordAuthenticationToken.authenticated(
                        details,
                        null,
                        details.getAuthorities()
                );
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenReturn(authenticationResult);
        when(jwtTokenService.createAccessToken(any(AuthenticatedUser.class)))
                .thenReturn("access-token");
        when(jwtTokenService.createRefreshToken(any(AuthenticatedUser.class)))
                .thenReturn("refresh-token");

        TokenResponse response = authService.login(
                new LoginRequest(" ZHOU@EXAMPLE.COM ", "raw-password")
        );

        ArgumentCaptor<Authentication> requestCaptor =
                ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(requestCaptor.capture());
        Authentication authenticationRequest = requestCaptor.getValue();
        assertFalse(authenticationRequest.isAuthenticated());
        assertEquals(" ZHOU@EXAMPLE.COM ", authenticationRequest.getPrincipal());
        assertEquals("raw-password", authenticationRequest.getCredentials());
        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(900L, response.expiresInSeconds());
        verify(refreshTokenStore).save(
                "u001",
                "refresh-token",
                jwtProperties.refreshTokenTtl()
        );
    }

    @Test
    void shouldTranslateExpectedLoginFailureWithoutIssuingTokens() {
        BadCredentialsException authenticationFailure =
                new BadCredentialsException("bad credentials");
        BusinessException expected = new BusinessException(
                AuthErrorCode.INVALID_CREDENTIALS
        );
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(authenticationFailure);
        when(failureTranslator.translate(authenticationFailure))
                .thenReturn(expected);

        BusinessException actual = assertThrows(
                BusinessException.class,
                () -> authService.login(new LoginRequest("zhou", "wrong"))
        );

        assertEquals(expected, actual);
        verifyNoInteractions(jwtTokenService, refreshTokenStore);
    }

    @Test
    void shouldRefreshWithLatestUserDataAndAtomicRotation() {
        prepareParsedRefreshToken();
        User latestUser = user(UserStatus.ACTIVE, "zhou-renamed");
        when(userMapper.findById("u001")).thenReturn(Optional.of(latestUser));
        when(jwtTokenService.createAccessToken(any(AuthenticatedUser.class)))
                .thenReturn("access-new");
        when(jwtTokenService.createRefreshToken(any(AuthenticatedUser.class)))
                .thenReturn("refresh-new");
        when(refreshTokenStore.rotate(
                "u001",
                CURRENT_REFRESH_TOKEN,
                "refresh-new",
                jwtProperties.refreshTokenTtl()
        )).thenReturn(true);

        TokenResponse response = authService.refresh(
                new RefreshTokenRequest(CURRENT_REFRESH_TOKEN)
        );

        AuthenticatedUser latestIdentity = new AuthenticatedUser(
                "u001",
                "zhou-renamed",
                Set.of("ROLE_USER")
        );
        verify(jwtTokenService).createAccessToken(latestIdentity);
        verify(jwtTokenService).createRefreshToken(latestIdentity);
        assertEquals("access-new", response.accessToken());
        assertEquals("refresh-new", response.refreshToken());
        verify(refreshTokenStore).rotate(
                "u001",
                CURRENT_REFRESH_TOKEN,
                "refresh-new",
                jwtProperties.refreshTokenTtl()
        );
    }

    @Test
    void shouldRejectExpiredRefreshTokenBeforeDatabaseLookup() {
        when(jwtTokenService.parse(CURRENT_REFRESH_TOKEN)).thenThrow(
                new ExpiredJwtException(null, null, "expired")
        );

        assertBusinessError(
                AuthErrorCode.REFRESH_TOKEN_EXPIRED,
                () -> authService.refresh(
                        new RefreshTokenRequest(CURRENT_REFRESH_TOKEN)
                )
        );

        verifyNoInteractions(userMapper, refreshTokenStore);
    }

    @Test
    void shouldRejectMalformedRefreshTokenBeforeDatabaseLookup() {
        when(jwtTokenService.parse(CURRENT_REFRESH_TOKEN)).thenThrow(
                new MalformedJwtException("malformed")
        );

        assertBusinessError(
                AuthErrorCode.REFRESH_TOKEN_INVALID,
                () -> authService.refresh(
                        new RefreshTokenRequest(CURRENT_REFRESH_TOKEN)
                )
        );

        verifyNoInteractions(userMapper, refreshTokenStore);
    }

    @Test
    void shouldRejectRefreshTokenWhenUserNoLongerExists() {
        prepareParsedRefreshToken();
        when(userMapper.findById("u001")).thenReturn(Optional.empty());

        assertBusinessError(
                AuthErrorCode.REFRESH_TOKEN_INVALID,
                () -> authService.refresh(
                        new RefreshTokenRequest(CURRENT_REFRESH_TOKEN)
                )
        );

        verify(refreshTokenStore, never()).rotate(
                any(), any(), any(), any()
        );
    }

    @Test
    void shouldRevokeRefreshSessionWhenLatestAccountIsDisabled() {
        prepareParsedRefreshToken();
        when(userMapper.findById("u001"))
                .thenReturn(Optional.of(user(UserStatus.DISABLED, "zhou")));

        assertBusinessError(
                AuthErrorCode.ACCOUNT_DISABLED,
                () -> authService.refresh(
                        new RefreshTokenRequest(CURRENT_REFRESH_TOKEN)
                )
        );

        verify(refreshTokenStore).delete("u001", CURRENT_REFRESH_TOKEN);
        verify(jwtTokenService, never()).createAccessToken(any());
    }

    @Test
    void shouldRevokeRefreshSessionWhenLatestAccountIsLocked() {
        prepareParsedRefreshToken();
        when(userMapper.findById("u001"))
                .thenReturn(Optional.of(user(UserStatus.LOCKED, "zhou")));

        assertBusinessError(
                AuthErrorCode.ACCOUNT_LOCKED,
                () -> authService.refresh(
                        new RefreshTokenRequest(CURRENT_REFRESH_TOKEN)
                )
        );

        verify(refreshTokenStore).delete("u001", CURRENT_REFRESH_TOKEN);
        verify(jwtTokenService, never()).createAccessToken(any());
    }

    @Test
    void shouldRejectAlreadyRotatedRefreshToken() {
        prepareParsedRefreshToken();
        when(userMapper.findById("u001"))
                .thenReturn(Optional.of(user(UserStatus.ACTIVE, "zhou")));
        when(jwtTokenService.createAccessToken(any(AuthenticatedUser.class)))
                .thenReturn("access-new");
        when(jwtTokenService.createRefreshToken(any(AuthenticatedUser.class)))
                .thenReturn("refresh-new");
        when(refreshTokenStore.rotate(
                any(), any(), any(), any()
        )).thenReturn(false);

        assertBusinessError(
                AuthErrorCode.REFRESH_TOKEN_INVALID,
                () -> authService.refresh(
                        new RefreshTokenRequest(CURRENT_REFRESH_TOKEN)
                )
        );
    }

    @Test
    void shouldDelegateIdempotentLogoutToMatchingDelete() {
        authService.logout("u001", CURRENT_REFRESH_TOKEN);

        verify(refreshTokenStore).delete("u001", CURRENT_REFRESH_TOKEN);
    }

    private void prepareParsedRefreshToken() {
        when(jwtTokenService.parse(CURRENT_REFRESH_TOKEN)).thenReturn(claims);
        when(jwtTokenService.toRefreshToken(claims)).thenReturn(
                new ParsedRefreshToken("u001", "token-id")
        );
    }

    private User user(UserStatus status, String username) {
        return new User(
                "u001",
                username,
                "zhou@example.com",
                "$2a$12$" + "A".repeat(53),
                "小周",
                null,
                status,
                0,
                NOW,
                NOW
        );
    }

    private void assertBusinessError(
            AuthErrorCode expected,
            Runnable invocation
    ) {
        BusinessException exception = assertThrows(
                BusinessException.class,
                invocation::run
        );
        assertEquals(expected, exception.getErrorCode());
    }
}
