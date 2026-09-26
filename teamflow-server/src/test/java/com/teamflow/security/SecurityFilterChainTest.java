package com.teamflow.security;

import com.teamflow.common.api.ApiResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 无状态接口权限清单与安全过滤器链集成测试。 */
@WebMvcTest(
        controllers = SecurityFilterChainTest.ProbeController.class,
        properties = "teamflow.cors.allowed-origins=http://localhost:5173"
)
@EnableConfigurationProperties(CorsProperties.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        SecurityErrorResponseWriter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityFilterChainTest.ProbeController.class
})
class SecurityFilterChainTest {

    private static final AuthenticatedUser USER = new AuthenticatedUser(
            "u001",
            "zhou",
            Set.of("ROLE_USER")
    );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private PasswordVerifiedAccountStatusChecker accountStatusChecker;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPermitPublicLoginWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/auth/login"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(jsonPath("$.code").value("COMMON_0000"));
    }

    @Test
    void shouldReturnUnifiedUnauthorizedResponseWithoutCreatingSession()
            throws Exception {
        MvcResult result = mockMvc.perform(get("/api/test/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer"
                ))
                .andExpect(jsonPath("$.code").value("COMMON_0003"))
                .andReturn();

        assertNull(result.getRequest().getSession(false));
    }

    @Test
    void shouldAllowAuthenticatedBearerRequestWithoutCreatingSession()
            throws Exception {
        prepareValidAccessToken("valid-access");

        MvcResult result = mockMvc.perform(
                        post("/api/test/protected")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer valid-access"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(content().string("u001"))
                .andReturn();

        assertNull(result.getRequest().getSession(false));
    }

    @Test
    void shouldRejectRefreshTokenInAuthorizationHeader() throws Exception {
        Claims claims = mock(Claims.class);
        when(jwtTokenService.parse("refresh-token")).thenReturn(claims);
        when(jwtTokenService.toAuthenticatedUser(claims)).thenThrow(
                new MalformedJwtException("wrong token type")
        );

        mockMvc.perform(
                        get("/api/test/protected")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer refresh-token"
                                )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_0006"));
    }

    @Test
    void shouldReturnForbiddenForAuthenticatedRequestOutsideAllowedRules()
            throws Exception {
        prepareValidAccessToken("valid-access");

        mockMvc.perform(
                        get("/admin-only")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer valid-access"
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON_0004"));
    }

    @Test
    void shouldKeepHealthAndOpenApiPublicButProtectActuatorDetails()
            throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_0003"));
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_0003"));
        mockMvc.perform(get("/actuator/metrics/jvm.memory.used"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_0003"));
    }

    @Test
    void shouldAllowAuthenticatedAccessToProtectedActuatorDetails()
            throws Exception {
        prepareValidAccessToken("valid-access");

        mockMvc.perform(
                        get("/actuator/metrics")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer valid-access"
                                )
                )
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowCorsPreflightOnlyForConfiguredOrigin() throws Exception {
        mockMvc.perform(
                        options("/api/test/protected")
                                .header(
                                        HttpHeaders.ORIGIN,
                                        "http://localhost:5173"
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "GET"
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                        "Authorization,Last-Event-ID"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:5173"
                ));

        mockMvc.perform(
                        options("/api/test/protected")
                                .header(
                                        HttpHeaders.ORIGIN,
                                        "https://untrusted.example.com"
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "GET"
                                )
                )
                .andExpect(status().isForbidden());
    }

    private void prepareValidAccessToken(String token) {
        Claims claims = mock(Claims.class);
        when(jwtTokenService.parse(token)).thenReturn(claims);
        when(jwtTokenService.toAuthenticatedUser(claims)).thenReturn(USER);
    }

    @RestController
    public static class ProbeController {

        @PostMapping("/api/auth/login")
        public ApiResponse<String> login() {
            return ApiResponse.success("public");
        }

        @GetMapping("/api/test/protected")
        public String protectedGet(
                @AuthenticationPrincipal AuthenticatedUser user
        ) {
            return user.id();
        }

        @PostMapping("/api/test/protected")
        public String protectedPost(
                @AuthenticationPrincipal AuthenticatedUser user
        ) {
            return user.id();
        }

        @GetMapping("/admin-only")
        public String deniedByDefault() {
            return "should-not-run";
        }

        @GetMapping({"/actuator/health", "/v3/api-docs"})
        public String publicOperationsProbe() {
            return "public";
        }

        @GetMapping({
                "/actuator/info",
                "/actuator/metrics",
                "/actuator/metrics/{meterName}"
        })
        public String protectedOperationsProbe() {
            return "protected";
        }
    }
}
