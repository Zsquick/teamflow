package com.teamflow.core.user.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 用户状态业务规则测试。 */
class UserStatusTest {

    @Test
    void shouldOnlyAllowActiveUserToAuthenticate() {
        assertTrue(UserStatus.ACTIVE.canAuthenticate());
        assertFalse(UserStatus.DISABLED.canAuthenticate());
        assertFalse(UserStatus.LOCKED.canAuthenticate());
    }

    @Test
    void shouldAllowIdempotentTransition() {
        for (UserStatus status : UserStatus.values()) {
            assertTrue(status.canTransitionTo(status));
        }
    }

    @Test
    void shouldAllowSupportedTransitions() {
        assertTrue(UserStatus.ACTIVE.canTransitionTo(UserStatus.DISABLED));
        assertTrue(UserStatus.ACTIVE.canTransitionTo(UserStatus.LOCKED));
        assertTrue(UserStatus.DISABLED.canTransitionTo(UserStatus.ACTIVE));
        assertTrue(UserStatus.LOCKED.canTransitionTo(UserStatus.ACTIVE));
        assertTrue(UserStatus.LOCKED.canTransitionTo(UserStatus.DISABLED));
    }

    @Test
    void shouldRejectDisabledToLockedTransition() {
        assertFalse(UserStatus.DISABLED.canTransitionTo(UserStatus.LOCKED));
    }

    @Test
    void shouldRejectNullTargetStatus() {
        assertThrows(
                NullPointerException.class,
                () -> UserStatus.ACTIVE.canTransitionTo(null)
        );
    }
}
