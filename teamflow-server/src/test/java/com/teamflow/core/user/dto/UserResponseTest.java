package com.teamflow.core.user.dto;

import com.teamflow.core.user.domain.User;
import com.teamflow.core.user.domain.UserStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 用户响应转换测试。 */
class UserResponseTest {

    @Test
    void shouldCreateSafeResponseFromUser() {
        User user = User.create(
                "u001",
                "zhou",
                "zhou@example.com",
                "$2a$10$" + "A".repeat(53),
                "小周",
                "2026-09-07T10:20:30.123Z"
        );

        UserResponse response = UserResponse.from(user);

        assertEquals("u001", response.id());
        assertEquals("zhou", response.username());
        assertEquals("zhou@example.com", response.email());
        assertEquals("小周", response.displayName());
        assertNull(response.avatarUrl());
        assertEquals(UserStatus.ACTIVE, response.status());
    }
}
