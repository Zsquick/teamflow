package com.teamflow.core.auth.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 认证请求和响应的诊断文本不得泄露密码或令牌。 */
class AuthSensitiveDtoToStringTest {

    private static final String PASSWORD = "Never-Log-This-Password";
    private static final String ACCESS_TOKEN = "access.secret.value";
    private static final String REFRESH_TOKEN = "refresh.secret.value";

    @Test
    void shouldHideRawPasswordFromRequestDiagnosticText() {
        String registerText = new RegisterRequest(
                "zhou",
                "zhou@example.com",
                PASSWORD,
                "小周"
        ).toString();
        String loginText = new LoginRequest(
                "zhou",
                PASSWORD
        ).toString();

        assertAll(
                () -> assertFalse(registerText.contains(PASSWORD)),
                () -> assertFalse(loginText.contains(PASSWORD)),
                () -> assertTrue(registerText.contains("[PROTECTED]")),
                () -> assertTrue(loginText.contains("[PROTECTED]"))
        );
    }

    @Test
    void shouldHideRawTokensFromDiagnosticText() {
        String requestText = new RefreshTokenRequest(
                REFRESH_TOKEN
        ).toString();
        String responseText = TokenResponse.bearer(
                ACCESS_TOKEN,
                REFRESH_TOKEN,
                900
        ).toString();

        assertAll(
                () -> assertFalse(requestText.contains(REFRESH_TOKEN)),
                () -> assertFalse(responseText.contains(ACCESS_TOKEN)),
                () -> assertFalse(responseText.contains(REFRESH_TOKEN)),
                () -> assertTrue(requestText.contains("[PROTECTED]")),
                () -> assertTrue(responseText.contains("[PROTECTED]"))
        );
    }
}
