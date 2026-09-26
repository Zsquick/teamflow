package com.teamflow.security;

import com.teamflow.core.user.domain.User;
import com.teamflow.core.user.domain.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spring Security 用户详情适配测试。 */
class TeamFlowUserDetailsTest {

    private static final String PASSWORD_HASH =
            "$2a$12$" + "A".repeat(53);
    private static final String NOW = "2026-09-09T10:20:30.123Z";

    @Test
    void shouldAdaptIdentityPasswordAndImmutableBaseAuthority() {
        TeamFlowUserDetails details = TeamFlowUserDetails.from(
                userWith(UserStatus.ACTIVE)
        );

        assertEquals("u001", details.getId());
        assertEquals("zhou", details.getUsername());
        assertEquals(PASSWORD_HASH, details.getPassword());
        assertEquals(
                Set.of("ROLE_USER"),
                authorityNames(details.getAuthorities())
        );
        assertThrows(
                UnsupportedOperationException.class,
                details.getAuthorities()::clear
        );
    }

    @ParameterizedTest
    @MethodSource("accountStates")
    void shouldMapAccountStatePrecisely(
            UserStatus status,
            boolean enabled,
            boolean accountNonLocked
    ) {
        TeamFlowUserDetails details = TeamFlowUserDetails.from(
                userWith(status)
        );

        assertEquals(enabled, details.isEnabled());
        assertEquals(accountNonLocked, details.isAccountNonLocked());
        assertTrue(details.isAccountNonExpired());
        assertTrue(details.isCredentialsNonExpired());
    }

    @Test
    void shouldEraseOnlyCredentialsAndAllowRepeatedErase() {
        TeamFlowUserDetails details = TeamFlowUserDetails.from(
                userWith(UserStatus.ACTIVE)
        );

        details.eraseCredentials();
        details.eraseCredentials();

        assertNull(details.getPassword());
        assertEquals("u001", details.getId());
        assertEquals("zhou", details.getUsername());
        assertTrue(details.isEnabled());
        assertEquals(
                Set.of("ROLE_USER"),
                authorityNames(details.getAuthorities())
        );
    }

    @Test
    void shouldCreatePasswordFreeAuthenticatedUser() {
        TeamFlowUserDetails details = TeamFlowUserDetails.from(
                userWith(UserStatus.ACTIVE)
        );

        AuthenticatedUser authenticatedUser = details.toAuthenticatedUser();

        assertEquals("u001", authenticatedUser.id());
        assertEquals("zhou", authenticatedUser.username());
        assertEquals(Set.of("ROLE_USER"), authenticatedUser.authorities());
    }

    @Test
    void shouldRejectMissingUserAndNeverExposePasswordInText() {
        assertThrows(
                NullPointerException.class,
                () -> TeamFlowUserDetails.from(null)
        );

        TeamFlowUserDetails details = TeamFlowUserDetails.from(
                userWith(UserStatus.LOCKED)
        );
        assertFalse(details.toString().contains(PASSWORD_HASH));
        assertTrue(details.toString().contains("u001"));
    }

    private static Stream<Arguments> accountStates() {
        return Stream.of(
                Arguments.of(UserStatus.ACTIVE, true, true),
                Arguments.of(UserStatus.DISABLED, false, true),
                Arguments.of(UserStatus.LOCKED, true, false)
        );
    }

    private static Set<String> authorityNames(
            Collection<? extends GrantedAuthority> authorities
    ) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toSet());
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
