package com.teamflow.core.user.domain;

import com.teamflow.common.error.BusinessException;
import com.teamflow.common.error.CommonErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 用户实体业务规则测试。 */
class UserTest {

    private static final String PASSWORD_HASH =
            "$2a$10$" + "A".repeat(53);
    private static final String CREATED_AT = "2026-09-07T10:20:30.123Z";

    @Test
    void shouldCreateActiveUserWithPreparedIdentity() {
        User user = User.create(
                "u001",
                "zhou_01",
                "zhou@example.com",
                PASSWORD_HASH,
                " 小周 ",
                CREATED_AT
        );

        assertEquals("u001", user.getId());
        assertEquals("zhou_01", user.getUsername());
        assertEquals("zhou@example.com", user.getEmail());
        assertEquals(PASSWORD_HASH, user.getPasswordHash());
        assertEquals("小周", user.getDisplayName());
        assertNull(user.getAvatarUrl());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertEquals(0, user.getVersion());
        assertEquals(CREATED_AT, user.getCreatedAt());
        assertEquals(CREATED_AT, user.getUpdatedAt());
    }

    @Test
    void shouldRejectInvalidOrInconsistentPersistentState() {
        assertThrows(
                IllegalArgumentException.class,
                () -> restoredUser(
                        UserStatus.ACTIVE,
                        -1,
                        CREATED_AT,
                        CREATED_AT
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> restoredUser(
                        UserStatus.ACTIVE,
                        0,
                        CREATED_AT,
                        "2026-09-07T10:20:30Z"
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> restoredUser(
                        UserStatus.ACTIVE,
                        0,
                        CREATED_AT,
                        "2026-09-07T10:20:30.122Z"
                )
        );
    }

    @Test
    void shouldChangeStatusVersionAndUpdatedTimeTogether() {
        User user = newUser();

        boolean changed = user.changeStatus(
                UserStatus.LOCKED,
                "2026-09-07T10:21:00.000Z"
        );

        assertTrue(changed);
        assertEquals(UserStatus.LOCKED, user.getStatus());
        assertEquals(1, user.getVersion());
        assertEquals("2026-09-07T10:21:00.000Z", user.getUpdatedAt());
    }

    @Test
    void shouldTreatSameStatusAsNoOp() {
        User user = newUser();

        boolean changed = user.changeStatus(
                UserStatus.ACTIVE,
                "2026-09-07T10:21:00.000Z"
        );

        assertFalse(changed);
        assertEquals(0, user.getVersion());
        assertEquals(CREATED_AT, user.getUpdatedAt());
    }

    @Test
    void shouldRejectUnsupportedTransitionWithoutChangingUser() {
        User user = restoredUser(
                UserStatus.DISABLED,
                3,
                CREATED_AT,
                "2026-09-07T10:21:00.000Z"
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> user.changeStatus(
                        UserStatus.LOCKED,
                        "2026-09-07T10:22:00.000Z"
                )
        );

        assertEquals(CommonErrorCode.CONFLICT, exception.getErrorCode());
        assertEquals(UserStatus.DISABLED, user.getStatus());
        assertEquals(3, user.getVersion());
        assertEquals("2026-09-07T10:21:00.000Z", user.getUpdatedAt());
    }

    @Test
    void shouldRejectStatusChangeEarlierThanCurrentUpdate() {
        User user = newUser();

        assertThrows(
                IllegalArgumentException.class,
                () -> user.changeStatus(
                        UserStatus.DISABLED,
                        "2026-09-07T10:20:30.122Z"
                )
        );
    }

    @Test
    void shouldKeepStateWhenVersionOverflows() {
        User user = restoredUser(
                UserStatus.ACTIVE,
                Integer.MAX_VALUE,
                CREATED_AT,
                CREATED_AT
        );

        assertThrows(
                IllegalStateException.class,
                () -> user.changeStatus(
                        UserStatus.DISABLED,
                        "2026-09-07T10:21:00.000Z"
                )
        );

        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertEquals(Integer.MAX_VALUE, user.getVersion());
        assertEquals(CREATED_AT, user.getUpdatedAt());
    }

    private User newUser() {
        return User.create(
                "u001",
                "zhou",
                "zhou@example.com",
                PASSWORD_HASH,
                "小周",
                CREATED_AT
        );
    }

    private User restoredUser(
            UserStatus status,
            int version,
            String createdAt,
            String updatedAt
    ) {
        return new User(
                "u001",
                "zhou",
                "zhou@example.com",
                PASSWORD_HASH,
                "小周",
                null,
                status,
                version,
                createdAt,
                updatedAt
        );
    }
}
