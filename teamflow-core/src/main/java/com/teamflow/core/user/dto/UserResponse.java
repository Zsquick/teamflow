package com.teamflow.core.user.dto;

import com.teamflow.core.user.domain.User;
import com.teamflow.core.user.domain.UserStatus;

import java.util.Objects;

/**
 * 对外展示的用户信息。
 *
 * @param id 用户标识
 * @param username 用户名
 * @param email 邮箱
 * @param displayName 展示名称
 * @param avatarUrl 头像地址
 * @param status 账号状态
 */
public record UserResponse(
        String id,
        String username,
        String email,
        String displayName,
        String avatarUrl,
        UserStatus status
) {

    /**
     * 将用户实体转换成不包含密码摘要的接口响应。
     *
     * @param user 用户实体
     * @return 用户响应
     */
    public static UserResponse from(User user) {
        Objects.requireNonNull(user, "用户实体不能为 null");
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getStatus()
        );
    }
}
