package com.teamflow.security;

/**
 * 从已验证刷新令牌中提取的最小身份。
 *
 * @param userId 用户编号，例如 {@code u001}
 * @param tokenId JWT 的唯一 {@code jti}
 */
public record ParsedRefreshToken(String userId, String tokenId) {
}
