package com.teamflow.security;

import com.teamflow.core.user.domain.User;
import com.teamflow.core.user.domain.UserStatus;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 将 TeamFlow 用户实体适配成 Spring Security 能够认证的用户详情。
 *
 * <p>该对象只在认证流程中短暂存在。业务代码和 JWT 使用不含密码摘要的
 * {@link AuthenticatedUser}，避免把敏感凭据带入后续业务流程。</p>
 */
public final class TeamFlowUserDetails
        implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Set<GrantedAuthority> BASE_AUTHORITIES = Set.of(
            new SimpleGrantedAuthority("ROLE_USER")
    );

    private final String id;
    private final String username;
    private final UserStatus status;
    private final Set<GrantedAuthority> authorities;
    private String passwordHash;

    private TeamFlowUserDetails(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.passwordHash = user.getPasswordHash();
        this.status = user.getStatus();
        this.authorities = BASE_AUTHORITIES;
    }

    /**
     * 返回 TeamFlow 的可读用户编号。
     *
     * @return 形如 {@code u001} 的用户编号
     */
    public String getId() {
        return id;
    }

    /**
     * 返回认证时用于 BCrypt 比对的密码摘要。
     *
     * @return BCrypt 摘要；调用 {@link #eraseCredentials()} 后为 {@code null}
     */
    @Override
    public String getPassword() {
        return passwordHash;
    }

    /**
     * 返回已经规范化并保存的用户名。
     *
     * @return 用户名
     */
    @Override
    public String getUsername() {
        return username;
    }

    /**
     * 返回由服务端授予的全局权限。
     *
     * @return 不可修改的权限集合
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /**
     * 当前模型没有账号过期时间，因此账号始终未过期。
     *
     * @return 固定为 {@code true}
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * 将 TeamFlow 的锁定状态转换为 Spring Security 的状态。
     *
     * @return 非 {@link UserStatus#LOCKED} 时返回 {@code true}
     */
    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.LOCKED;
    }

    /**
     * 当前模型没有密码过期时间，因此凭据始终未过期。
     *
     * @return 固定为 {@code true}
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 将 TeamFlow 的停用状态转换为 Spring Security 的状态。
     *
     * @return 非 {@link UserStatus#DISABLED} 时返回 {@code true}
     */
    @Override
    public boolean isEnabled() {
        return status != UserStatus.DISABLED;
    }

    /**
     * 在密码验证结束后清除内存中的密码摘要引用。
     */
    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }

    /**
     * 使用数据库还原出的用户实体创建认证适配对象。
     *
     * @param user 用户实体
     * @return Spring Security 用户详情
     */
    public static TeamFlowUserDetails from(User user) {
        return new TeamFlowUserDetails(
                Objects.requireNonNull(user, "用户实体不能为 null")
        );
    }

    /**
     * 在认证成功后生成不含密码摘要的业务身份。
     *
     * @return 可安全放入业务认证上下文或 JWT 的身份
     */
    public AuthenticatedUser toAuthenticatedUser() {
        Set<String> authorityNames = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        return new AuthenticatedUser(id, username, authorityNames);
    }

    /**
     * 返回不包含密码摘要的诊断文本。
     *
     * @return 安全的用户详情文本
     */
    @Override
    public String toString() {
        return "TeamFlowUserDetails["
                + "id=" + id
                + ", username=" + username
                + ", status=" + status
                + ", authorities=" + authorities
                + ']';
    }
}
