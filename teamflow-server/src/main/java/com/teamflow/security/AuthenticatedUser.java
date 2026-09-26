package com.teamflow.security;

import java.util.Objects;
import java.util.Set;

/**
 * 从已验证令牌中构造的当前用户身份。
 *
 * @param id 用户标识
 * @param username 用户名
 * @param authorities 权限集合
 */
public record AuthenticatedUser(String id, String username, Set<String> authorities) {

    /**
     * 创建已经通过认证的当前用户身份。
     */
    public AuthenticatedUser {
        Objects.requireNonNull(id, "认证用户编号不能为 null");
        Objects.requireNonNull(username, "认证用户名不能为 null");
        authorities = Set.copyOf(
                Objects.requireNonNull(authorities, "权限集合不能为 null")
        );
    }
}
