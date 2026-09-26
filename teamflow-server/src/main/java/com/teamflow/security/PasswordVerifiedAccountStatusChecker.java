package com.teamflow.security;

import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.stereotype.Component;

/**
 * 在密码已经验证正确之后检查账号和凭据状态。
 *
 * <p>该类必须配置为 {@code DaoAuthenticationProvider} 的后置检查器，不能作为
 * 前置检查器使用，否则会在验证密码之前暴露账号状态。</p>
 */
@Component
public final class PasswordVerifiedAccountStatusChecker
        implements UserDetailsChecker {

    /**
     * 检查已经通过密码验证的用户是否允许完成认证。
     *
     * @param userDetails 已通过密码验证的用户详情
     * @throws LockedException 账号已锁定
     * @throws DisabledException 账号已停用
     * @throws AccountExpiredException 账号已过期
     * @throws CredentialsExpiredException 凭据已过期
     */
    @Override
    public void check(UserDetails userDetails) {
        if (!userDetails.isAccountNonLocked()) {
            throw new LockedException("用户账号已锁定");
        }
        if (!userDetails.isEnabled()) {
            throw new DisabledException("用户账号已停用");
        }
        if (!userDetails.isAccountNonExpired()) {
            throw new AccountExpiredException("用户账号已过期");
        }
        if (!userDetails.isCredentialsNonExpired()) {
            throw new CredentialsExpiredException("用户凭据已过期");
        }
    }
}
