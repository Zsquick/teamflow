package com.teamflow.core.auth.dto;

import com.teamflow.core.auth.validation.PasswordLength;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 用户登录请求。
 *
 * @param identifier 用户名或邮箱
 * @param password 原始密码
 */
public record LoginRequest(
        @NotBlank(message = "登录标识不能为空")
        @Size(max = 128, message = "登录标识不能超过 128 个字符")
        String identifier,

        @NotBlank(message = "密码不能为空")
        @PasswordLength(minCodePoints = 0, maxUtf8Bytes = 72)
        String password
) {

    /**
     * 返回适合诊断的请求摘要，但不暴露原始密码。
     *
     * @return 已隐藏密码的请求摘要
     */
    @Override
    public String toString() {
        return "LoginRequest["
                + "identifier=" + identifier
                + ", password=[PROTECTED]"
                + ']';
    }
}
