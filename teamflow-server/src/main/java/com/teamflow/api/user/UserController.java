package com.teamflow.api.user;

import com.teamflow.common.api.ApiResponse;
import com.teamflow.core.user.dto.UserResponse;
import com.teamflow.core.user.service.UserService;
import com.teamflow.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/** 当前用户资料接口。 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = Objects.requireNonNull(userService, "用户服务不能为 null");
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal AuthenticatedUser user) {
        UserResponse response = userService.getCurrentUser(user.id());
        return ApiResponse.success(response);
    }
}
