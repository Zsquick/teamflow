package com.teamflow.api.notification;

import com.teamflow.api.error.ApiExceptionHandler;
import com.teamflow.common.api.PageQuery;
import com.teamflow.common.api.PageResult;
import com.teamflow.core.notification.domain.NotificationType;
import com.teamflow.core.notification.dto.NotificationResponse;
import com.teamflow.core.notification.service.NotificationService;
import com.teamflow.security.AuthenticatedUser;
import com.teamflow.security.CorsProperties;
import com.teamflow.security.JwtAuthenticationFilter;
import com.teamflow.security.JwtTokenService;
import com.teamflow.security.PasswordVerifiedAccountStatusChecker;
import com.teamflow.security.RestAccessDeniedHandler;
import com.teamflow.security.RestAuthenticationEntryPoint;
import com.teamflow.security.SecurityConfig;
import com.teamflow.security.SecurityErrorResponseWriter;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 通知接口的 JWT、分页 Validation 与当前用户隔离测试。 */
@WebMvcTest(
        controllers = NotificationController.class,
        properties = "teamflow.cors.allowed-origins=http://localhost:5173"
)
@EnableConfigurationProperties(CorsProperties.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        SecurityErrorResponseWriter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        ApiExceptionHandler.class
})
class NotificationControllerTest {

    private static final String TOKEN = "valid-access";
    private static final String USER_ID = "u001";
    private static final String NOW = "2026-09-18T01:02:03.456Z";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private NotificationService notificationService;
    @MockitoBean
    private JwtTokenService jwtTokenService;
    @MockitoBean
    private UserDetailsService userDetailsService;
    @MockitoBean
    private PasswordVerifiedAccountStatusChecker accountStatusChecker;

    @BeforeEach
    void prepareToken() {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(jwtTokenService.parse(TOKEN)).thenReturn(claims);
        when(jwtTokenService.toAuthenticatedUser(claims)).thenReturn(
                new AuthenticatedUser(
                        USER_ID,
                        "zhou",
                        Set.of("ROLE_USER")
                )
        );
    }

    @Test
    void shouldRejectAnonymousRequest() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldListOnlyAuthenticatedUsersPage() throws Exception {
        NotificationResponse notification = new NotificationResponse(
                "n001",
                NotificationType.TASK_ASSIGNED,
                "任务指派",
                "任务 t001 已指派给你",
                false,
                NOW
        );
        when(notificationService.listMine(
                USER_ID,
                new PageQuery(2, 10)
        )).thenReturn(new PageResult<>(List.of(notification), 2, 10, 11));

        mockMvc.perform(get("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .param("page", "2")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value("n001"))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.total").value(11));
        verify(notificationService).listMine(
                USER_ID,
                new PageQuery(2, 10)
        );
    }

    @Test
    void shouldRejectInvalidPageSizeBeforeCallingService() throws Exception {
        mockMvc.perform(get("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnUnreadCountAndMarkOwnedNotificationRead()
            throws Exception {
        when(notificationService.countUnread(USER_ID)).thenReturn(3L);

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(3));
        mockMvc.perform(patch("/api/notifications/n001/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_0000"));

        verify(notificationService).countUnread(USER_ID);
        verify(notificationService).markRead(USER_ID, "n001");
    }
}
