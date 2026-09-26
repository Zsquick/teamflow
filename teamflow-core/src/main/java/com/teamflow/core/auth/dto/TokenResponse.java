package com.teamflow.core.auth.dto;

import com.teamflow.common.validation.TextValues;

/**
 * 登录或刷新成功后的令牌响应。
 *
 * @param accessToken 短期访问令牌
 * @param refreshToken 长期刷新令牌
 * @param tokenType HTTP Authorization 头使用的认证方案
 * @param expiresInSeconds 访问令牌剩余秒数
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds
) {

    /**
     * 保证令牌响应一经创建便可直接交给客户端使用。
     */
    public TokenResponse {
        TextValues.requireNonBlank(accessToken, "访问令牌");
        TextValues.requireNonBlank(refreshToken, "刷新令牌");
        TextValues.requireNonBlank(tokenType, "令牌类型");
        if (expiresInSeconds <= 0) {
            throw new IllegalArgumentException("访问令牌有效秒数必须大于 0");
        }
    }

    /**
     * 创建使用 Bearer 认证方案的令牌响应。
     *
     * @param accessToken 短期访问令牌
     * @param refreshToken 长期刷新令牌
     * @param expiresInSeconds 访问令牌剩余秒数
     * @return 可直接返回给客户端的令牌响应
     */
    public static TokenResponse bearer(
            String accessToken,
            String refreshToken,
            long expiresInSeconds
    ) {
        return new TokenResponse(
                accessToken,
                refreshToken,
                "Bearer",
                expiresInSeconds
        );
    }

    /**
     * 返回不包含访问令牌和刷新令牌原文的诊断文本。
     *
     * @return 已隐藏令牌的响应摘要
     */
    @Override
    public String toString() {
        return "TokenResponse["
                + "accessToken=[PROTECTED]"
                + ", refreshToken=[PROTECTED]"
                + ", tokenType=" + tokenType
                + ", expiresInSeconds=" + expiresInSeconds
                + ']';
    }
}
