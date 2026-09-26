package com.teamflow.security;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 当前认证用户身份测试。 */
class AuthenticatedUserTest {

    @Test
    void shouldKeepStringIdAndCopyAuthorities() {
        Set<String> source = new HashSet<>();
        source.add("ROLE_USER");

        AuthenticatedUser user = new AuthenticatedUser(
                "u001",
                "zhou",
                source
        );
        source.add("ROLE_ADMIN");

        assertEquals("u001", user.id());
        assertEquals(Set.of("ROLE_USER"), user.authorities());
        assertThrows(
                UnsupportedOperationException.class,
                () -> user.authorities().add("ROLE_ADMIN")
        );
    }

    @Test
    void shouldRejectMissingRequiredIdentityPart() {
        assertThrows(
                NullPointerException.class,
                () -> new AuthenticatedUser(null, "zhou", Set.of())
        );
        assertThrows(
                NullPointerException.class,
                () -> new AuthenticatedUser("u001", null, Set.of())
        );
        assertThrows(
                NullPointerException.class,
                () -> new AuthenticatedUser("u001", "zhou", null)
        );
    }
}
