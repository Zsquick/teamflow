package com.teamflow.application.auth;

import com.teamflow.common.error.BusinessException;
import com.teamflow.core.auth.dto.RegisterRequest;
import com.teamflow.core.auth.error.AuthErrorCode;
import com.teamflow.core.common.id.ReadableIdGenerator;
import com.teamflow.core.common.id.ResourceType;
import com.teamflow.core.user.domain.User;
import com.teamflow.core.user.domain.UserStatus;
import com.teamflow.core.user.dto.UserResponse;
import com.teamflow.core.user.mapper.UserMapper;
import com.teamflow.security.JwtProperties;
import com.teamflow.security.JwtTokenService;
import com.teamflow.security.RefreshTokenStore;
import com.teamflow.security.AuthenticationFailureTranslator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 用户注册业务测试。 */
@ExtendWith(MockitoExtension.class)
class AuthServiceRegisterTest {

    private static final String NOW = "2026-09-09T01:02:03.456Z";
    private static final String PASSWORD_HASH = "$2a$12$" + "A".repeat(53);

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
    private AuthenticationFailureTranslator authenticationFailureTranslator;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse(NOW), ZoneOffset.UTC);
        JwtProperties properties = new JwtProperties(
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
                clock,
                jwtTokenService,
                refreshTokenStore,
                properties,
                authenticationManager,
                authenticationFailureTranslator
        );
    }

    @Test
    void shouldRegisterNormalizedUserInOneFlow() {
        RegisterRequest request = request(" TeamFlow@2026 ");
        when(userMapper.countByUsername("zhou_01")).thenReturn(0L);
        when(userMapper.countByEmail("zhou@example.com")).thenReturn(0L);
        when(idGenerator.nextId(ResourceType.USER)).thenReturn("u001");
        when(passwordEncoder.encode(" TeamFlow@2026 ")).thenReturn(PASSWORD_HASH);
        when(userMapper.insert(any(User.class))).thenReturn(1);

        UserResponse response = authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertEquals("u001", response.id());
        assertEquals("zhou_01", response.username());
        assertEquals("zhou@example.com", response.email());
        assertEquals("小周", response.displayName());
        assertEquals(UserStatus.ACTIVE, response.status());
        assertEquals(PASSWORD_HASH, savedUser.getPasswordHash());
        assertEquals(NOW, savedUser.getCreatedAt());
        assertEquals(NOW, savedUser.getUpdatedAt());

        InOrder order = inOrder(userMapper, idGenerator, passwordEncoder);
        order.verify(userMapper).countByUsername("zhou_01");
        order.verify(userMapper).countByEmail("zhou@example.com");
        order.verify(passwordEncoder).encode(" TeamFlow@2026 ");
        order.verify(idGenerator).nextId(ResourceType.USER);
        order.verify(userMapper).insert(savedUser);
    }

    @Test
    void shouldStopWhenUsernameAlreadyExists() {
        when(userMapper.countByUsername("zhou_01")).thenReturn(1L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.register(request("TeamFlow@2026"))
        );

        assertEquals(AuthErrorCode.USERNAME_ALREADY_EXISTS, exception.getErrorCode());
        verify(userMapper, never()).countByEmail("zhou@example.com");
        verifyNoInteractions(idGenerator, passwordEncoder);
    }

    @Test
    void shouldStopWhenEmailAlreadyExists() {
        when(userMapper.countByUsername("zhou_01")).thenReturn(0L);
        when(userMapper.countByEmail("zhou@example.com")).thenReturn(1L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.register(request("TeamFlow@2026"))
        );

        assertEquals(AuthErrorCode.EMAIL_ALREADY_EXISTS, exception.getErrorCode());
        verifyNoInteractions(idGenerator, passwordEncoder);
    }

    @Test
    void shouldConvertConcurrentUniqueConflict() {
        prepareUntilInsert();
        when(userMapper.insert(any(User.class)))
                .thenThrow(new DuplicateKeyException("concurrent conflict"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.register(request("TeamFlow@2026"))
        );

        assertEquals(AuthErrorCode.REGISTRATION_CONFLICT, exception.getErrorCode());
    }

    @Test
    void shouldFailWhenInsertDoesNotAffectExactlyOneRow() {
        prepareUntilInsert();
        when(userMapper.insert(any(User.class))).thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> authService.register(request("TeamFlow@2026"))
        );
    }

    private RegisterRequest request(String password) {
        return new RegisterRequest(
                "Zhou_01",
                "ZHOU@EXAMPLE.COM",
                password,
                " 小周 "
        );
    }

    private void prepareUntilInsert() {
        when(userMapper.countByUsername("zhou_01")).thenReturn(0L);
        when(userMapper.countByEmail("zhou@example.com")).thenReturn(0L);
        when(idGenerator.nextId(ResourceType.USER)).thenReturn("u001");
        when(passwordEncoder.encode("TeamFlow@2026")).thenReturn(PASSWORD_HASH);
    }
}
