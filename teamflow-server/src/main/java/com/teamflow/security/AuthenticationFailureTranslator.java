package com.teamflow.security;

import com.teamflow.common.error.BusinessException;
import com.teamflow.core.auth.error.AuthErrorCode;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 将预期的 Spring Security 认证异常转换为稳定的业务错误码。
 *
 * <p>停用和锁定异常必须来自“先验证密码、再检查账号状态”的认证流程，
 * 才能安全地转换成具体状态；否则应对外统一为凭据错误，避免账号枚举。</p>
 */
@Component
public final class AuthenticationFailureTranslator {

    /**
     * 转换登录过程中可以安全公开的认证失败。
     *
     * <p>不在白名单中的异常通常表示认证服务或配置发生故障，本方法会将原异常
     * 继续抛出，使全局异常处理器记录日志并返回服务器错误。</p>
     *
     * @param exception Spring Security 认证异常
     * @return 携带稳定认证错误码的业务异常
     * @throws AuthenticationException 遇到未预期的认证异常时原样抛出
     */
    public BusinessException translate(AuthenticationException exception) {
        Objects.requireNonNull(exception, "认证异常不能为 null");

        AuthErrorCode errorCode = switch (exception) {
            case BadCredentialsException ignored ->
                    AuthErrorCode.INVALID_CREDENTIALS;
            case UsernameNotFoundException ignored ->
                    AuthErrorCode.INVALID_CREDENTIALS;
            case DisabledException ignored ->
                    AuthErrorCode.ACCOUNT_DISABLED;
            case LockedException ignored ->
                    AuthErrorCode.ACCOUNT_LOCKED;
            default -> throw exception;
        };

        return new BusinessException(errorCode);
    }
}
