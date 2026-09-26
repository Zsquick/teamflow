package com.teamflow.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamflow.security.AuthenticatedUser;
import com.teamflow.security.SecurityErrorResponseWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RequestRateLimitFilterTest {

    private final RequestRateLimiter limiter = mock(RequestRateLimiter.class);
    private final RateLimitProperties properties = properties(true);
    private final RequestRateLimitFilter filter = new RequestRateLimitFilter(
            limiter,
            properties,
            new SecurityErrorResponseWriter(
                    new ObjectMapper().findAndRegisterModules()
            )
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldLimitRegistrationBeforeContinuingFilterChain() throws Exception {
        MockHttpServletRequest request = post("/api/auth/register");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                chain
        );

        verify(limiter).check(
                "registration",
                "127.0.0.1",
                properties.registration()
        );
        assertNotNull(chain.getRequest());
    }

    @Test
    void shouldWriteUnifiedResponseWithoutContinuingWhenQuotaIsExceeded()
            throws Exception {
        doThrow(new RateLimitExceededException(17L))
                .when(limiter)
                .check(
                        same("registration"),
                        anyString(),
                        same(properties.registration())
                );
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(post("/api/auth/register"), response, chain);

        assertEquals(429, response.getStatus());
        assertEquals("17", response.getHeader(HttpHeaders.RETRY_AFTER));
        assertEquals("no-store", response.getHeader(HttpHeaders.CACHE_CONTROL));
        assertEquals("COMMON_0010", new ObjectMapper().readTree(
                response.getContentAsByteArray()
        ).path("code").asText());
        assertNull(chain.getRequest());
    }

    @Test
    void shouldUseAuthenticatedUserAsUploadSubject() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(
                "u001",
                "alice",
                Set.of("ROLE_USER")
        );
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        user,
                        null,
                        List.of()
                )
        );

        filter.doFilter(
                post("/api/tasks/t001/attachments"),
                new MockHttpServletResponse(),
                new MockFilterChain()
        );

        verify(limiter).check("upload", "u001", properties.upload());
    }

    @Test
    void shouldBypassNonPostRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/auth/login"
        );
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verifyNoInteractions(limiter);
        assertNotNull(chain.getRequest());
    }

    private static MockHttpServletRequest post(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                path
        );
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    private static RateLimitProperties properties(boolean enabled) {
        return new RateLimitProperties(
                enabled,
                new RateLimitProperties.Policy(10, Duration.ofMinutes(1)),
                new RateLimitProperties.Policy(5, Duration.ofMinutes(10)),
                new RateLimitProperties.Policy(30, Duration.ofMinutes(1)),
                new RateLimitProperties.Policy(30, Duration.ofMinutes(1))
        );
    }
}
