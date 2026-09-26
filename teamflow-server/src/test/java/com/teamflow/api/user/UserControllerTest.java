package com.teamflow.api.user;

import com.teamflow.common.api.ApiResponse;
import com.teamflow.core.user.domain.UserStatus;
import com.teamflow.core.user.dto.UserResponse;
import com.teamflow.core.user.service.UserService;
import com.teamflow.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 当前用户资料接口测试。 */
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    private UserController controller;

    @BeforeEach
    void setUp() {
        controller = new UserController(userService);
    }

    @Test
    void shouldQueryProfileWithAuthenticatedStringId() {
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                "u001",
                "zhou",
                Set.of("ROLE_USER")
        );
        UserResponse profile = new UserResponse(
                "u001",
                "zhou",
                "zhou@example.com",
                "小周",
                null,
                UserStatus.ACTIVE
        );
        when(userService.getCurrentUser("u001")).thenReturn(profile);

        ApiResponse<UserResponse> result = controller.me(authenticatedUser);

        assertEquals("COMMON_0000", result.code());
        assertEquals(profile, result.data());
        verify(userService).getCurrentUser("u001");
    }
}
