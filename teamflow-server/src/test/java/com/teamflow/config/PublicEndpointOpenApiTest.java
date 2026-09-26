package com.teamflow.config;

import com.teamflow.api.auth.AuthController;
import com.teamflow.core.auth.service.AuthService;
import com.teamflow.system.controller.SystemController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.junit.jupiter.api.Test;
import org.springdoc.core.service.SecurityService;
import org.springdoc.core.utils.PropertyResolverUtils;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

/** SecurityConfig 公开业务端点与 OpenAPI 安全声明的一致性测试。 */
class PublicEndpointOpenApiTest {

    @Test
    void shouldClearGlobalBearerRequirementForPublicOperations()
            throws Exception {
        List<Method> publicOperations = List.of(
                AuthController.class.getMethod(
                        "register",
                        com.teamflow.core.auth.dto.RegisterRequest.class
                ),
                AuthController.class.getMethod(
                        "login",
                        com.teamflow.core.auth.dto.LoginRequest.class
                ),
                AuthController.class.getMethod(
                        "refresh",
                        com.teamflow.core.auth.dto.RefreshTokenRequest.class
                ),
                SystemController.class.getMethod("ping")
        );

        for (Method method : publicOperations) {
            Operation operation = method.getAnnotation(Operation.class);
            SecurityRequirements securityRequirements = method.getAnnotation(
                    SecurityRequirements.class
            );
            assertAll(
                    () -> assertNotNull(
                            operation,
                            method + " 必须声明 OpenAPI 公开语义"
                    ),
                    () -> assertEquals(
                            0,
                            operation.security().length,
                            method + " 不应继承全局 Bearer 要求"
                    ),
                    () -> assertNotNull(
                            securityRequirements,
                            method + " 必须用空安全要求覆盖全局配置"
                    ),
                    () -> assertEquals(
                            0,
                            securityRequirements.value().length,
                            method + " 的覆盖项必须为空"
                    )
            );

            io.swagger.v3.oas.annotations.security.SecurityRequirement[]
                    resolved = securityService().getSecurityRequirements(
                            handlerMethod(method)
                    );
            assertNotNull(resolved);
            assertEquals(0, resolved.length);
        }
    }

    @Test
    void shouldKeepAuthenticatedLogoutOnGlobalBearerRequirement()
            throws Exception {
        Method logout = AuthController.class.getMethod(
                "logout",
                com.teamflow.security.AuthenticatedUser.class,
                com.teamflow.core.auth.dto.RefreshTokenRequest.class
        );

        assertNull(
                logout.getAnnotation(Operation.class),
                "退出登录仍应继承全局 Bearer 要求"
        );
        assertNull(
                securityService().getSecurityRequirements(
                        handlerMethod(logout)
                ),
                "退出登录未覆盖安全声明时必须继承全局 Bearer 要求"
        );
    }

    private SecurityService securityService() {
        return new SecurityService(mock(PropertyResolverUtils.class));
    }

    private HandlerMethod handlerMethod(Method method) {
        Object controller = method.getDeclaringClass() == AuthController.class
                ? new AuthController(mock(AuthService.class))
                : new SystemController();
        return new HandlerMethod(controller, method);
    }
}
