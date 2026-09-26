package com.teamflow.api.task;

import com.teamflow.api.error.ApiExceptionHandler;
import com.teamflow.common.api.PageQuery;
import com.teamflow.common.api.PageResult;
import com.teamflow.common.error.BusinessException;
import com.teamflow.core.task.domain.TaskPriority;
import com.teamflow.core.task.domain.TaskStatus;
import com.teamflow.core.task.dto.ChangeTaskStatusRequest;
import com.teamflow.core.task.dto.CreateTaskRequest;
import com.teamflow.core.task.dto.TaskResponse;
import com.teamflow.core.task.dto.UpdateTaskRequest;
import com.teamflow.core.task.error.TaskErrorCode;
import com.teamflow.core.task.service.TaskService;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 任务 REST 映射、Validation、JWT 和业务错误响应测试。 */
@WebMvcTest(
        controllers = TaskController.class,
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
class TaskControllerTest {

    private static final String TOKEN = "valid-access";
    private static final String USER_ID = "u001";
    private static final String PROJECT_ID = "p001";
    private static final String TASK_ID = "t001";
    private static final String NOW = "2026-09-16T02:03:04.567Z";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService taskService;
    @MockitoBean
    private JwtTokenService jwtTokenService;
    @MockitoBean
    private UserDetailsService userDetailsService;
    @MockitoBean
    private PasswordVerifiedAccountStatusChecker accountStatusChecker;

    @BeforeEach
    void prepareAccessToken() {
        Claims claims = mock(Claims.class);
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
    void shouldRequireTaskService() {
        assertThrows(
                NullPointerException.class,
                () -> new TaskController(null)
        );
    }

    @Test
    void shouldRejectProtectedTaskApiWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/tasks").param("projectId", PROJECT_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer"
                ))
                .andExpect(jsonPath("$.code").value("COMMON_0003"));
    }

    @Test
    void shouldRejectInvalidCreateBodyWithFieldErrors() throws Exception {
        mockMvc.perform(
                        authorized(post("/api/tasks"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "projectId": "p001",
                                          "title": "   ",
                                          "priority": "HIGH",
                                          "dueAt": "2026-09-16"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_0001"))
                .andExpect(jsonPath("$.data.title").isArray())
                .andExpect(jsonPath("$.data.dueAt").isArray());
    }

    @Test
    void shouldCreateTaskForAuthenticatedStringUser() throws Exception {
        CreateTaskRequest request = new CreateTaskRequest(
                PROJECT_ID,
                "实现任务",
                null,
                TaskPriority.HIGH,
                null,
                null
        );
        when(taskService.create(USER_ID, request)).thenReturn(response());

        mockMvc.perform(
                        authorized(post("/api/tasks"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "projectId": "p001",
                                          "title": "实现任务",
                                          "priority": "HIGH"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_0000"))
                .andExpect(jsonPath("$.data.id").value(TASK_ID))
                .andExpect(jsonPath("$.data.projectId").value(PROJECT_ID))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.createdAt").value(NOW));

        verify(taskService).create(USER_ID, request);
    }

    @Test
    void shouldApplyDefaultAndExplicitPagingParameters() throws Exception {
        PageQuery defaults = PageQuery.defaults();
        when(taskService.listByProject(
                USER_ID, PROJECT_ID, null, defaults
        )).thenReturn(PageResult.empty(defaults));

        mockMvc.perform(
                        authorized(get("/api/tasks"))
                                .param("projectId", PROJECT_ID)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20));
        verify(taskService).listByProject(
                USER_ID, PROJECT_ID, null, defaults
        );

        PageQuery explicit = new PageQuery(2, 10);
        when(taskService.listByProject(
                USER_ID, PROJECT_ID, TaskStatus.TODO, explicit
        )).thenReturn(PageResult.empty(explicit));
        mockMvc.perform(
                        authorized(get("/api/tasks"))
                                .param("projectId", PROJECT_ID)
                                .param("status", "TODO")
                                .param("page", "2")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(10));
        verify(taskService).listByProject(
                USER_ID, PROJECT_ID, TaskStatus.TODO, explicit
        );
    }

    @Test
    void shouldRejectInvalidPageSizeAndMissingDeleteVersion()
            throws Exception {
        mockMvc.perform(
                        authorized(get("/api/tasks"))
                                .param("projectId", PROJECT_ID)
                                .param("size", "101")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_0001"));

        mockMvc.perform(authorized(delete("/api/tasks/{id}", TASK_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_0001"));
    }

    @Test
    void shouldUseMinimalStatusCommandForBoardMove() throws Exception {
        ChangeTaskStatusRequest request = new ChangeTaskStatusRequest(
                TaskStatus.IN_PROGRESS,
                0
        );
        TaskResponse changed = new TaskResponse(
                TASK_ID, PROJECT_ID, "实现任务", null,
                TaskStatus.IN_PROGRESS, TaskPriority.HIGH,
                null, USER_ID, null, 1, NOW, NOW
        );
        when(taskService.changeStatus(USER_ID, TASK_ID, request))
                .thenReturn(changed);

        mockMvc.perform(
                        authorized(patch("/api/tasks/{id}/status", TASK_ID))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "status": "IN_PROGRESS",
                                          "version": 0
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status")
                        .value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.version").value(1));
        verify(taskService).changeStatus(USER_ID, TASK_ID, request);
    }

    @Test
    void shouldReturnStableConflictResponseFromUpdate() throws Exception {
        when(taskService.update(
                any(),
                any(),
                any(UpdateTaskRequest.class)
        )).thenThrow(new BusinessException(
                TaskErrorCode.TASK_VERSION_CONFLICT
        ));

        mockMvc.perform(
                        authorized(put("/api/tasks/{id}", TASK_ID))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "title": "实现任务",
                                          "status": "TODO",
                                          "priority": "HIGH",
                                          "version": 0
                                        }
                                        """)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_0004"));
    }

    @Test
    void shouldDeleteWithExpectedVersionAndReturnEmptyData()
            throws Exception {
        mockMvc.perform(
                        authorized(delete("/api/tasks/{id}", TASK_ID))
                                .param("version", "2")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_0000"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(taskService).delete(USER_ID, TASK_ID, 2);
    }

    private MockHttpServletRequestBuilder authorized(
            MockHttpServletRequestBuilder request
    ) {
        return request.header(
                HttpHeaders.AUTHORIZATION,
                "Bearer " + TOKEN
        );
    }

    private TaskResponse response() {
        return new TaskResponse(
                TASK_ID,
                PROJECT_ID,
                "实现任务",
                null,
                TaskStatus.TODO,
                TaskPriority.HIGH,
                null,
                USER_ID,
                null,
                0,
                NOW,
                NOW
        );
    }
}
