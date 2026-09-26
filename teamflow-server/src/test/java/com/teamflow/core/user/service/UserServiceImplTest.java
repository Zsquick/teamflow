package com.teamflow.core.user.service;

import com.teamflow.common.error.BusinessException;
import com.teamflow.common.error.CommonErrorCode;
import com.teamflow.core.user.domain.User;
import com.teamflow.core.user.dto.UserResponse;
import com.teamflow.core.user.mapper.UserMapper;
import com.teamflow.core.user.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 用户资料业务测试。 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userMapper);
    }

    @Test
    void shouldReturnCurrentUserProfile() {
        User user = newUser();
        when(userMapper.findById("u001")).thenReturn(Optional.of(user));

        UserResponse response = userService.getCurrentUser("u001");

        assertEquals("u001", response.id());
        assertEquals("zhou", response.username());
        verify(userMapper).findById("u001");
    }

    @Test
    void shouldReportNotFoundWhenCurrentUserDoesNotExist() {
        when(userMapper.findById("u404")).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.getCurrentUser("u404")
        );

        assertEquals(CommonErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
    }

    private User newUser() {
        return User.create(
                "u001",
                "zhou",
                "zhou@example.com",
                "$2a$10$" + "A".repeat(53),
                "小周",
                "2026-09-07T10:20:30.123Z"
        );
    }
}
