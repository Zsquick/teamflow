package com.teamflow.security;

import com.teamflow.core.user.domain.User;
import com.teamflow.core.user.domain.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 密码优先认证顺序测试。 */
class PasswordFirstAuthenticationProviderTest {

    private static final String CORRECT_PASSWORD = "TeamFlow@2026";
    private static final String WRONG_PASSWORD = "wrong-password";
    private static final String NOW = "2026-09-09T10:20:30.123Z";

    private final SecurityConfig securityConfig = new SecurityConfig();
    private final PasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder(4);
    private final PasswordVerifiedAccountStatusChecker accountStatusChecker =
            new PasswordVerifiedAccountStatusChecker();

    @ParameterizedTest
    @EnumSource(
            value = UserStatus.class,
            names = {"DISABLED", "LOCKED"}
    )
    void shouldHideAccountStatusWhenPasswordIsWrong(UserStatus status) {
        AuthenticationManager manager = managerFor(
                ignored -> teamFlowDetails(status)
        );

        assertThrows(
                BadCredentialsException.class,
                () -> authenticate(manager, WRONG_PASSWORD)
        );
    }

    @ParameterizedTest
    @MethodSource("verifiedAccountFailures")
    void shouldReportAccountStatusOnlyAfterPasswordMatches(
            UserStatus status,
            Class<? extends AuthenticationException> expectedType
    ) {
        AuthenticationManager manager = managerFor(
                ignored -> teamFlowDetails(status)
        );

        assertThrows(
                expectedType,
                () -> authenticate(manager, CORRECT_PASSWORD)
        );
    }

    @Test
    void shouldAuthenticateActiveUserAndEraseCredentials() {
        AuthenticationManager manager = managerFor(
                ignored -> teamFlowDetails(UserStatus.ACTIVE)
        );

        Authentication result = authenticate(manager, CORRECT_PASSWORD);

        assertTrue(result.isAuthenticated());
        assertNull(result.getCredentials());
        TeamFlowUserDetails principal = assertInstanceOf(
                TeamFlowUserDetails.class,
                result.getPrincipal()
        );
        assertNull(principal.getPassword());
    }

    @Test
    void shouldHideMissingUserAsBadCredentials() {
        AuthenticationManager manager = managerFor(identifier -> {
            throw new UsernameNotFoundException("用户不存在");
        });

        assertThrows(
                BadCredentialsException.class,
                () -> authenticate(manager, CORRECT_PASSWORD)
        );
    }

    @ParameterizedTest
    @MethodSource("verifiedExpiryFailures")
    void shouldFailClosedForFutureExpiryStates(
            boolean accountExpired,
            boolean credentialsExpired,
            Class<? extends AuthenticationException> expectedType
    ) {
        UserDetails details = springUser(
                accountExpired,
                credentialsExpired
        );
        AuthenticationManager manager = managerFor(ignored -> details);

        assertThrows(
                expectedType,
                () -> authenticate(manager, CORRECT_PASSWORD)
        );
    }

    private AuthenticationManager managerFor(
            UserDetailsService userDetailsService
    ) {
        DaoAuthenticationProvider provider =
                securityConfig.authenticationProvider(
                        userDetailsService,
                        passwordEncoder,
                        accountStatusChecker
                );
        return securityConfig.authenticationManager(provider);
    }

    private Authentication authenticate(
            AuthenticationManager manager,
            String password
    ) {
        return manager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        "zhou",
                        password
                )
        );
    }

    private TeamFlowUserDetails teamFlowDetails(UserStatus status) {
        User user = new User(
                "u001",
                "zhou",
                "zhou@example.com",
                passwordEncoder.encode(CORRECT_PASSWORD),
                "小周",
                null,
                status,
                0,
                NOW,
                NOW
        );
        return TeamFlowUserDetails.from(user);
    }

    private static Stream<Arguments> verifiedAccountFailures() {
        return Stream.of(
                Arguments.of(UserStatus.DISABLED, DisabledException.class),
                Arguments.of(UserStatus.LOCKED, LockedException.class)
        );
    }

    private static Stream<Arguments> verifiedExpiryFailures() {
        return Stream.of(
                Arguments.of(
                        true,
                        false,
                        AccountExpiredException.class
                ),
                Arguments.of(
                        false,
                        true,
                        CredentialsExpiredException.class
                )
        );
    }

    private UserDetails springUser(
            boolean accountExpired,
            boolean credentialsExpired
    ) {
        return org.springframework.security.core.userdetails.User
                .withUsername("zhou")
                .password(passwordEncoder.encode(CORRECT_PASSWORD))
                .authorities("ROLE_USER")
                .accountExpired(accountExpired)
                .credentialsExpired(credentialsExpired)
                .build();
    }
}
