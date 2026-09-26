package com.teamflow.system.controller;

import com.teamflow.security.CorsProperties;
import com.teamflow.security.JwtAuthenticationFilter;
import com.teamflow.security.JwtTokenService;
import com.teamflow.security.PasswordVerifiedAccountStatusChecker;
import com.teamflow.security.RestAccessDeniedHandler;
import com.teamflow.security.RestAuthenticationEntryPoint;
import com.teamflow.security.SecurityConfig;
import com.teamflow.security.SecurityErrorResponseWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 固定系统探活接口的 HTTP 契约。
 */
@WebMvcTest(
        controllers = SystemController.class,
        properties = "teamflow.cors.allowed-origins=http://localhost:5173"
)
@EnableConfigurationProperties(CorsProperties.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        SecurityErrorResponseWriter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class SystemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private PasswordVerifiedAccountStatusChecker accountStatusChecker;

    /**
     * 验证探活接口返回 HTTP 200 以及约定的统一响应字段。
     *
     * @throws Exception MockMvc 请求或断言失败时抛出
     */
    @Test
    void shouldReturnPong() throws Exception {
        mockMvc.perform(get("/api/v1/system/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_0000"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data").value("pong"));
    }
}
