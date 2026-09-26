package com.teamflow.security;

/**
 * TeamFlow JWT 的两种用途。
 *
 * <p>该类型只在 JWT 基础设施内部使用，令牌中保存的是稳定的小写文本，
 * 而不是可能随 Java 枚举命名调整而变化的 {@link #name()}。</p>
 */
enum JwtTokenType {
    ACCESS("access"),
    REFRESH("refresh");

    private final String claimValue;

    JwtTokenType(String claimValue) {
        this.claimValue = claimValue;
    }

    String claimValue() {
        return claimValue;
    }
}
