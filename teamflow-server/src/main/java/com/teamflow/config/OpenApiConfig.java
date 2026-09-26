package com.teamflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** TeamFlow OpenAPI 元数据和 JWT Bearer 描述。 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI teamFlowOpenApi() {
        SecurityScheme bearer = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("使用登录接口返回的 access token");
        return new OpenAPI()
                .info(new Info()
                        .title("TeamFlow REST API")
                        .version("1.0.0")
                        .description("团队、项目、任务、文件、通知与批量导入接口"))
                .components(new Components().addSecuritySchemes(
                        BEARER_SCHEME,
                        bearer
                ))
                .addSecurityItem(
                        new SecurityRequirement().addList(BEARER_SCHEME)
                );
    }
}
