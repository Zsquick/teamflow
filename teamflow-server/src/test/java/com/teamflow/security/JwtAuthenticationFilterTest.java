package com.teamflow.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bearer JWT 恢复 Spring Security 身份的过滤器测试。 */
class JwtAuthenticationFilterTest {

    private static final Instant NOW = Instant.parse("2026-09-10T02:00:00Z");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final JwtProperties JWT_PROPERTIES = new JwtProperties(
            "teamflow",
            Duration.ofMinutes(15),
            Duration.ofDays(7),
            Base64.getEncoder().encodeToString(
                    "0123456789abcdef0123456789abcdef"
                            .getBytes(StandardCharsets.UTF_8)
            )
    );
    private static final AuthenticatedUser USER = new AuthenticatedUser(
            "u001",
            "zhou",
            Set.of("ROLE_USER")
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldContinueWithoutCreatingIdentityWhenHeaderIsAbsent()
            throws Exception {
        JwtAuthenticationFilter filter = filterAt(NOW);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean();

        filter.doFilter(
                request,
                response,
                (ignoredRequest, ignoredResponse) -> continued.set(true)
        );

        assertTrue(continued.get());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldRestoreAuthenticatedUserFromValidAccessToken()
            throws Exception {
        JwtTokenService tokenService = tokenServiceAt(NOW);
        JwtAuthenticationFilter filter = filter(tokenService);
        MockHttpServletRequest request = bearerRequest(
                tokenService.createAccessToken(USER)
        );
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean();

        filter.doFilter(
                request,
                response,
                (ignoredRequest, ignoredResponse) -> continued.set(true)
        );

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        assertTrue(continued.get());
        assertTrue(authentication.isAuthenticated());
        assertEquals(USER, authentication.getPrincipal());
        assertEquals(
                Set.of("ROLE_USER"),
                authentication.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .collect(java.util.stream.Collectors.toSet())
        );
        assertInstanceOf(
                org.springframework.security.web.authentication.WebAuthenticationDetails.class,
                authentication.getDetails()
        );
    }

    @Test
    void shouldRejectRefreshTokenUsedAsAccessToken() throws Exception {
        JwtTokenService tokenService = tokenServiceAt(NOW);

        Rejection rejection = executeRejected(
                filter(tokenService),
                tokenService.createRefreshToken(USER)
        );

        assertFalse(rejection.continued());
        assertEquals(401, rejection.response().getStatus());
        assertEquals("AUTH_0006", rejection.code());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldRejectExpiredAccessTokenWithSpecificCode() throws Exception {
        JwtTokenService issuingService = tokenServiceAt(NOW);
        String token = issuingService.createAccessToken(USER);
        JwtAuthenticationFilter laterFilter = filterAt(
                NOW.plus(Duration.ofMinutes(15)).plusSeconds(1)
        );

        Rejection rejection = executeRejected(laterFilter, token);

        assertFalse(rejection.continued());
        assertEquals(401, rejection.response().getStatus());
        assertEquals("AUTH_0007", rejection.code());
    }

    @Test
    void shouldRejectTokenWithTamperedSignature() throws Exception {
        JwtTokenService tokenService = tokenServiceAt(NOW);
        String token = tokenService.createAccessToken(USER);
        String[] sections = token.split("\\.");
        char replacement = sections[2].charAt(0) == 'A' ? 'B' : 'A';
        sections[2] = replacement + sections[2].substring(1);

        Rejection rejection = executeRejected(
                filter(tokenService),
                String.join(".", sections)
        );

        assertFalse(rejection.continued());
        assertEquals("AUTH_0006", rejection.code());
    }

    @Test
    void shouldTreatBearerSchemeCaseInsensitively() throws Exception {
        JwtTokenService tokenService = tokenServiceAt(NOW);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(
                HttpHeaders.AUTHORIZATION,
                "bearer " + tokenService.createAccessToken(USER)
        );
        AtomicBoolean continued = new AtomicBoolean();

        filter(tokenService).doFilter(
                request,
                new MockHttpServletResponse(),
                (ignoredRequest, ignoredResponse) -> continued.set(true)
        );

        assertTrue(continued.get());
        assertEquals(
                USER,
                SecurityContextHolder.getContext()
                        .getAuthentication()
                        .getPrincipal()
        );
    }

    private static JwtAuthenticationFilter filterAt(Instant instant) {
        return filter(tokenServiceAt(instant));
    }

    private static JwtAuthenticationFilter filter(JwtTokenService service) {
        return new JwtAuthenticationFilter(
                service,
                new SecurityErrorResponseWriter(OBJECT_MAPPER)
        );
    }

    private static JwtTokenService tokenServiceAt(Instant instant) {
        return new JwtTokenService(
                JWT_PROPERTIES,
                Clock.fixed(instant, ZoneOffset.UTC)
        );
    }

    private static MockHttpServletRequest bearerRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return request;
    }

    private static Rejection executeRejected(
            JwtAuthenticationFilter filter,
            String token
    ) throws Exception {
        MockHttpServletRequest request = bearerRequest(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean();

        filter.doFilter(
                request,
                response,
                (ignoredRequest, ignoredResponse) -> continued.set(true)
        );

        JsonNode body = OBJECT_MAPPER.readTree(
                response.getContentAsByteArray()
        );
        return new Rejection(
                continued.get(),
                response,
                body.get("code").asText()
        );
    }

    private record Rejection(
            boolean continued,
            MockHttpServletResponse response,
            String code
    ) {
    }
}
