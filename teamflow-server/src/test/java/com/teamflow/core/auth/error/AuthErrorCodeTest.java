package com.teamflow.core.auth.error;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 认证业务错误码协议测试。 */
class AuthErrorCodeTest {

    @Test
    void shouldExposeStableRegistrationAndLoginMappings() {
        assertMapping(
                AuthErrorCode.USERNAME_ALREADY_EXISTS,
                "AUTH_0001",
                "用户名已被使用",
                409
        );
        assertMapping(
                AuthErrorCode.EMAIL_ALREADY_EXISTS,
                "AUTH_0002",
                "邮箱已被使用",
                409
        );
        assertMapping(
                AuthErrorCode.INVALID_CREDENTIALS,
                "AUTH_0003",
                "用户名、邮箱或密码不正确",
                401
        );
        assertMapping(AuthErrorCode.ACCOUNT_DISABLED, "AUTH_0004", "账号已停用", 403);
        assertMapping(AuthErrorCode.ACCOUNT_LOCKED, "AUTH_0005", "账号暂时锁定", 403);
        assertMapping(
                AuthErrorCode.REGISTRATION_CONFLICT,
                "AUTH_0010",
                "注册信息已被其他请求占用，请重新提交",
                409
        );
    }

    @Test
    void shouldExposeStableTokenMappings() {
        assertMapping(
                AuthErrorCode.ACCESS_TOKEN_INVALID,
                "AUTH_0006",
                "访问凭证无效，请重新登录",
                401
        );
        assertMapping(
                AuthErrorCode.ACCESS_TOKEN_EXPIRED,
                "AUTH_0007",
                "访问凭证已过期",
                401
        );
        assertMapping(
                AuthErrorCode.REFRESH_TOKEN_INVALID,
                "AUTH_0008",
                "刷新凭证无效，请重新登录",
                401
        );
        assertMapping(
                AuthErrorCode.REFRESH_TOKEN_EXPIRED,
                "AUTH_0009",
                "刷新凭证已过期，请重新登录",
                401
        );
    }

    @Test
    void shouldKeepEveryBusinessCodeUnique() {
        Set<String> codes = Arrays.stream(AuthErrorCode.values())
                .map(AuthErrorCode::code)
                .collect(Collectors.toSet());

        assertEquals(AuthErrorCode.values().length, codes.size());
    }

    private void assertMapping(
            AuthErrorCode errorCode,
            String code,
            String message,
            int httpStatus
    ) {
        assertEquals(code, errorCode.code());
        assertEquals(message, errorCode.message());
        assertEquals(httpStatus, errorCode.httpStatus());
    }
}
