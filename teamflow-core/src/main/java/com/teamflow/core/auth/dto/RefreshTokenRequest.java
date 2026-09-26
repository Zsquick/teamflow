package com.teamflow.core.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 刷新访问令牌请求。
 *
 * @param refreshToken 刷新令牌
 */
public record RefreshTokenRequest(
        @NotBlank(message = "刷新令牌不能为空")
        @Size(max = 4096, message = "刷新令牌不能超过 4096 个字符")
        String refreshToken
) {

    /**
     * 返回不包含刷新令牌原文的诊断文本。
     *
     * @return 已隐藏刷新令牌的请求摘要
     */
    @Override
    public String toString() {
        return "RefreshTokenRequest[refreshToken=[PROTECTED]]";
    }
}
