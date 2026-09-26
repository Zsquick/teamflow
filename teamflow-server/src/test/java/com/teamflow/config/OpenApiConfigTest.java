package com.teamflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** OpenAPI 元数据与 Bearer JWT 声明测试。 */
class OpenApiConfigTest {

    @Test
    void shouldDescribeGlobalBearerJwtAuthentication() {
        OpenAPI openAPI = new OpenApiConfig().teamFlowOpenApi();
        SecurityScheme scheme = openAPI.getComponents()
                .getSecuritySchemes()
                .get("bearerAuth");

        assertEquals("TeamFlow REST API", openAPI.getInfo().getTitle());
        assertEquals("1.0.0", openAPI.getInfo().getVersion());
        assertEquals(SecurityScheme.Type.HTTP, scheme.getType());
        assertEquals("bearer", scheme.getScheme());
        assertEquals("JWT", scheme.getBearerFormat());
        assertTrue(openAPI.getSecurity().getFirst().containsKey("bearerAuth"));
    }
}
