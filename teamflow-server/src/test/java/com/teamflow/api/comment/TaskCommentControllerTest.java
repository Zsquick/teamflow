package com.teamflow.api.comment;

import com.teamflow.api.error.ApiExceptionHandler;
import com.teamflow.common.error.BusinessException;
import com.teamflow.core.comment.dto.CommentResponse;
import com.teamflow.core.comment.dto.CreateCommentRequest;
import com.teamflow.core.comment.error.CommentErrorCode;
import com.teamflow.core.comment.service.TaskCommentService;
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

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 评论 REST 映射、Validation、JWT 和业务错误测试。 */
@WebMvcTest(
        controllers = TaskCommentController.class,
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
class TaskCommentControllerTest {

    private static final String TOKEN = "valid-access";
    private static final String USER_ID = "u001";
    private static final String TASK_ID = "t001";
    private static final String COMMENT_ID = "c001";
    private static final String NOW = "2026-09-18T01:02:03.456Z";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskCommentService commentService;
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
    void shouldRequireCommentService() {
        assertThrows(
                NullPointerException.class,
                () -> new TaskCommentController(null)
        );
    }

    @Test
    void shouldRejectProtectedCommentApiWithoutJwt() throws Exception {
        mockMvc.perform(get(
                        "/api/tasks/{taskId}/comments",
                        TASK_ID
                ))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer"
                ))
                .andExpect(jsonPath("$.code").value("COMMON_0003"));
    }

    @Test
    void shouldRejectBlankCreateBodyWithFieldError() throws Exception {
        mockMvc.perform(
                        authorized(post(
                                "/api/tasks/{taskId}/comments",
                                TASK_ID
                        ))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "content": "   "
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_0001"))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void shouldRejectOversizedCreateBody() throws Exception {
        String body = """
                {"content":"%s"}
                """.formatted("评".repeat(2001));

        mockMvc.perform(
                        authorized(post(
                                "/api/tasks/{taskId}/comments",
                                TASK_ID
                        ))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_0001"))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void shouldCreateNormalizedCommentForAuthenticatedUser()
            throws Exception {
        CreateCommentRequest request = new CreateCommentRequest(
                "  请检查  "
        );
        when(commentService.create(USER_ID, TASK_ID, request))
                .thenReturn(response());

        mockMvc.perform(
                        authorized(post(
                                "/api/tasks/{taskId}/comments",
                                TASK_ID
                        ))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "content": "  请检查  "
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_0000"))
                .andExpect(jsonPath("$.data.id").value(COMMENT_ID))
                .andExpect(jsonPath("$.data.authorId").value(USER_ID))
                .andExpect(jsonPath("$.data.authorName").value("小周"))
                .andExpect(jsonPath("$.data.content").value("请检查"))
                .andExpect(jsonPath("$.data.createdAt").value(NOW));

        verify(commentService).create(USER_ID, TASK_ID, request);
    }

    @Test
    void shouldListTaskComments() throws Exception {
        when(commentService.listByTask(USER_ID, TASK_ID))
                .thenReturn(List.of(response()));

        mockMvc.perform(
                        authorized(get(
                                "/api/tasks/{taskId}/comments",
                                TASK_ID
                        ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(COMMENT_ID))
                .andExpect(jsonPath("$.data[0].taskId").value(TASK_ID));

        verify(commentService).listByTask(USER_ID, TASK_ID);
    }

    @Test
    void shouldReturnStableForbiddenResponseForOtherAuthor()
            throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(
                CommentErrorCode.COMMENT_DELETE_FORBIDDEN
        )).when(commentService).delete(USER_ID, COMMENT_ID);

        mockMvc.perform(
                        authorized(delete(
                                "/api/comments/{commentId}",
                                COMMENT_ID
                        ))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMENT_0002"))
                .andExpect(jsonPath("$.message")
                        .value("只能删除自己发表的评论"));
    }

    @Test
    void shouldDeleteOwnCommentAndReturnEmptyData() throws Exception {
        mockMvc.perform(
                        authorized(delete(
                                "/api/comments/{commentId}",
                                COMMENT_ID
                        ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_0000"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(commentService).delete(USER_ID, COMMENT_ID);
    }

    private MockHttpServletRequestBuilder authorized(
            MockHttpServletRequestBuilder request
    ) {
        return request.header(
                HttpHeaders.AUTHORIZATION,
                "Bearer " + TOKEN
        );
    }

    private CommentResponse response() {
        return new CommentResponse(
                COMMENT_ID,
                TASK_ID,
                USER_ID,
                "小周",
                "请检查",
                NOW
        );
    }
}
