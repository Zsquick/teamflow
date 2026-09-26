package com.teamflow.security;

import com.teamflow.audit.RequestTraceFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** CORS 白名单及浏览器请求头配置测试。 */
class SecurityConfigTest {

    private static final String WEB_ORIGIN = "https://app.teamflow.example";
    private static final String ADMIN_ORIGIN = "https://admin.teamflow.example";

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void corsConfigurationUsesOnlyConfiguredOriginsForApiRequests() {
        CorsProperties properties = new CorsProperties(List.of(
                "  " + WEB_ORIGIN + "  ",
                ADMIN_ORIGIN,
                WEB_ORIGIN
        ));
        CorsConfigurationSource source = securityConfig
                .corsConfigurationSource(properties);
        CorsConfiguration configuration = configurationFor(
                source,
                "/api/tasks"
        );

        assertAll(
                () -> assertEquals(
                        List.of(WEB_ORIGIN, ADMIN_ORIGIN),
                        configuration.getAllowedOrigins()
                ),
                () -> assertEquals(
                        WEB_ORIGIN,
                        configuration.checkOrigin(WEB_ORIGIN)
                ),
                () -> assertNull(configuration.checkOrigin(
                        "https://untrusted.example"
                )),
                () -> assertNull(source.getCorsConfiguration(requestFor(
                        "/actuator/health"
                )))
        );
    }

    @Test
    void corsConfigurationAllowsRequiredApiShapeWithoutCredentials() {
        CorsConfiguration configuration = configurationFor(
                securityConfig.corsConfigurationSource(
                        new CorsProperties(List.of(WEB_ORIGIN))
                ),
                "/api/v1/events"
        );

        assertAll(
                () -> assertEquals(
                        List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"),
                        configuration.getAllowedMethods()
                ),
                () -> assertEquals(
                        List.of(
                                HttpHeaders.AUTHORIZATION,
                                HttpHeaders.CONTENT_TYPE,
                                HttpHeaders.ACCEPT,
                                "Last-Event-ID"
                        ),
                        configuration.getAllowedHeaders()
                ),
                () -> assertEquals(
                        List.of(
                                HttpHeaders.CONTENT_DISPOSITION,
                                HttpHeaders.RETRY_AFTER,
                                RequestTraceFilter.TRACE_ID_HEADER
                        ),
                        configuration.getExposedHeaders()
                ),
                () -> assertEquals(
                        Boolean.FALSE,
                        configuration.getAllowCredentials()
                ),
                () -> assertEquals(
                        3600L,
                        configuration.getMaxAge()
                )
        );
    }

    @Test
    void corsPropertiesRequireAtLeastOneNonBlankOrigin() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CorsProperties(List.of(" ", "  "))
        );
    }

    @Test
    void corsPropertiesRejectWildcardOrigin() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CorsProperties(List.of("*"))
        );
    }

    private static CorsConfiguration configurationFor(
            CorsConfigurationSource source,
            String requestUri
    ) {
        CorsConfiguration configuration = source.getCorsConfiguration(
                requestFor(requestUri)
        );
        assertNotNull(configuration);
        return configuration;
    }

    private static MockHttpServletRequest requestFor(String requestUri) {
        return new MockHttpServletRequest(
                HttpMethod.OPTIONS.name(),
                requestUri
        );
    }
}
