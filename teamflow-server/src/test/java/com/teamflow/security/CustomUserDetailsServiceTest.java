package com.teamflow.security;

import com.teamflow.core.user.domain.User;
import com.teamflow.core.user.domain.UserStatus;
import com.teamflow.core.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** 用户认证数据加载服务测试。 */
@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    private static final String PASSWORD_HASH =
            "$2a$12$" + "A".repeat(53);
    private static final String NOW = "2026-09-09T10:20:30.123Z";

    @Mock
    private UserMapper userMapper;

    private CustomUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        userDetailsService = new CustomUserDetailsService(userMapper);
    }

    @Test
    void shouldNormalizeIdentifierOnceAndLoadUser() {
        User user = userWith(UserStatus.ACTIVE);
        when(userMapper.findByIdentifier("zhou@example.com"))
                .thenReturn(Optional.of(user));

        TeamFlowUserDetails details = userDetailsService.loadUserByUsername(
                " ZHOU@EXAMPLE.COM "
        );

        assertEquals("u001", details.getId());
        assertEquals("zhou", details.getUsername());
        assertEquals(PASSWORD_HASH, details.getPassword());
        verify(userMapper).findByIdentifier("zhou@example.com");
        verifyNoMoreInteractions(userMapper);
    }

    @ParameterizedTest
    @EnumSource(
            value = UserStatus.class,
            names = {"DISABLED", "LOCKED"}
    )
    void shouldReturnNonActiveUserForSecurityStatusCheck(UserStatus status) {
        User user = userWith(status);
        when(userMapper.findByIdentifier("zhou"))
                .thenReturn(Optional.of(user));

        TeamFlowUserDetails details =
                userDetailsService.loadUserByUsername("zhou");

        assertEquals("u001", details.getId());
        if (status == UserStatus.DISABLED) {
            assertFalse(details.isEnabled());
        } else {
            assertFalse(details.isAccountNonLocked());
        }
        verify(userMapper).findByIdentifier("zhou");
    }

    @Test
    void shouldUseGenericMessageWhenUserDoesNotExist() {
        when(userMapper.findByIdentifier("missing@example.com"))
                .thenReturn(Optional.empty());

        UsernameNotFoundException exception = assertThrows(
                UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername(
                        " Missing@Example.com "
                )
        );

        assertEquals("登录标识或密码错误", exception.getMessage());
        verify(userMapper).findByIdentifier("missing@example.com");
    }

    @Test
    void shouldCreateFreshDetailsForEveryLoad() {
        User user = userWith(UserStatus.ACTIVE);
        when(userMapper.findByIdentifier("zhou"))
                .thenReturn(Optional.of(user));

        TeamFlowUserDetails first =
                userDetailsService.loadUserByUsername("zhou");
        first.eraseCredentials();
        TeamFlowUserDetails second =
                userDetailsService.loadUserByUsername("zhou");

        assertNotSame(first, second);
        assertNull(first.getPassword());
        assertEquals(PASSWORD_HASH, second.getPassword());
    }

    @Test
    void shouldRejectMissingMapperAtConstruction() {
        assertThrows(
                NullPointerException.class,
                () -> new CustomUserDetailsService(null)
        );
        verifyNoInteractions(userMapper);
    }

    private static User userWith(UserStatus status) {
        return new User(
                "u001",
                "zhou",
                "zhou@example.com",
                PASSWORD_HASH,
                "小周",
                null,
                status,
                0,
                NOW,
                NOW
        );
    }
}
