package com.teamflow.core.auth.error;

import com.teamflow.common.error.ErrorCode;

/**
 * 注册、登录和令牌处理使用的认证业务错误码。
 */
public enum AuthErrorCode implements ErrorCode {
    USERNAME_ALREADY_EXISTS("AUTH_0001", "用户名已被使用", 409),
    EMAIL_ALREADY_EXISTS("AUTH_0002", "邮箱已被使用", 409),
    INVALID_CREDENTIALS("AUTH_0003", "用户名、邮箱或密码不正确", 401),
    ACCOUNT_DISABLED("AUTH_0004", "账号已停用", 403),
    ACCOUNT_LOCKED("AUTH_0005", "账号暂时锁定", 403),
    ACCESS_TOKEN_INVALID("AUTH_0006", "访问凭证无效，请重新登录", 401),
    ACCESS_TOKEN_EXPIRED("AUTH_0007", "访问凭证已过期", 401),
    REFRESH_TOKEN_INVALID("AUTH_0008", "刷新凭证无效，请重新登录", 401),
    REFRESH_TOKEN_EXPIRED("AUTH_0009", "刷新凭证已过期，请重新登录", 401),
    REGISTRATION_CONFLICT("AUTH_0010", "注册信息已被其他请求占用，请重新提交", 409);

    private final String code;
    private final String message;
    private final int httpStatus;

    AuthErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }
}
