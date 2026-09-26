package com.teamflow.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamflow.core.auth.error.AuthErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 安全过滤链统一 JSON 错误响应测试。 */
class SecurityErrorResponseWriterTest {

    private ObjectMapper objectMapper;
    private SecurityErrorResponseWriter writer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        writer = new SecurityErrorResponseWriter(objectMapper);
    }

    @Test
    void shouldWriteTokenErrorAsUtf8JsonWithoutCaching() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(response, AuthErrorCode.ACCESS_TOKEN_INVALID);

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertEquals(401, response.getStatus());
        assertEquals(
                "application/json;charset=UTF-8",
                response.getContentType()
        );
        assertEquals(StandardCharsets.UTF_8.name(), response.getCharacterEncoding());
        assertEquals("no-store", response.getHeader(HttpHeaders.CACHE_CONTROL));
        assertEquals("Bearer", response.getHeader(HttpHeaders.WWW_AUTHENTICATE));
        assertEquals("AUTH_0006", body.get("code").asText());
        assertEquals("访问凭证无效，请重新登录", body.get("message").asText());
        assertTrue(body.get("data").isNull());
    }

    @Test
    void shouldUseUnauthorizedEntryPointForMissingAuthentication()
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        RestAuthenticationEntryPoint entryPoint =
                new RestAuthenticationEntryPoint(writer);

        entryPoint.commence(
                new MockHttpServletRequest(),
                response,
                new AuthenticationCredentialsNotFoundException("missing")
        );

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertEquals(401, response.getStatus());
        assertEquals("COMMON_0003", body.get("code").asText());
        assertEquals("Bearer", response.getHeader(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void shouldUseForbiddenHandlerForAuthenticatedUserWithoutPermission()
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        RestAccessDeniedHandler deniedHandler =
                new RestAccessDeniedHandler(writer);

        deniedHandler.handle(
                new MockHttpServletRequest(),
                response,
                new AccessDeniedException("denied")
        );

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertEquals(403, response.getStatus());
        assertEquals("COMMON_0004", body.get("code").asText());
        assertEquals("no-store", response.getHeader(HttpHeaders.CACHE_CONTROL));
        assertNull(response.getHeader(HttpHeaders.WWW_AUTHENTICATE));
    }
}
