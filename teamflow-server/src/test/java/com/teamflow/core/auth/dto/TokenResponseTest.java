package com.teamflow.core.auth.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 令牌响应对象测试。 */
class TokenResponseTest {

    @Test
    void shouldCreateBearerTokenResponse() {
        TokenResponse response = TokenResponse.bearer(
                "access-token",
                "refresh-token",
                900
        );

        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(900, response.expiresInSeconds());
    }

    @Test
    void shouldRejectMissingTokenValues() {
        assertThrows(
                NullPointerException.class,
                () -> new TokenResponse(null, "refresh", "Bearer", 1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new TokenResponse("access", " ", "Bearer", 1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new TokenResponse("access", "refresh", " ", 1)
        );
    }

    @Test
    void shouldRejectNonPositiveExpiry() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TokenResponse.bearer("access", "refresh", 0)
        );
    }
}
