package com.teamflow.core.auth.dto;

import com.teamflow.core.auth.validation.PasswordLength;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 用户注册请求。
 *
 * @param username 登录用户名
 * @param email 登录邮箱
 * @param password 原始密码
 * @param displayName 展示名称
 */
public record RegisterRequest(
        @NotBlank
        @Pattern(
                regexp = "[A-Za-z][A-Za-z0-9_]{2,31}",
                message = "用户名必须以字母开头，且只能包含字母、数字和下划线"
        )
        String username,
        @NotBlank @Email @Size(max = 128) String email,
        @NotBlank(message = "密码不能为空")
        @PasswordLength(minCodePoints = 8, maxUtf8Bytes = 72)
        String password,
        @NotBlank @Size(max = 64) String displayName
) {

    /**
     * 返回适合诊断的请求摘要，但不暴露原始密码。
     *
     * @return 已隐藏密码的请求摘要
     */
    @Override
    public String toString() {
        return "RegisterRequest["
                + "username=" + username
                + ", email=" + email
                + ", password=[PROTECTED]"
                + ", displayName=" + displayName
                + ']';
    }
}
