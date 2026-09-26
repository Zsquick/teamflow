package com.teamflow.api.error;

import com.teamflow.common.error.BusinessException;
import com.teamflow.common.error.CommonErrorCode;
import com.teamflow.core.auth.error.AuthErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 全局异常处理器的 MockMvc 测试。 */
class ApiExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldConvertBusinessExceptionToConfiguredHttpResponse() throws Exception {
        mockMvc.perform(get("/test/errors/business"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(CommonErrorCode.RESOURCE_NOT_FOUND.code()))
                .andExpect(jsonPath("$.message").value(CommonErrorCode.RESOURCE_NOT_FOUND.message()))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void shouldIncludeBearerChallengeForBusinessUnauthorizedResponse()
            throws Exception {
        mockMvc.perform(get("/test/errors/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer"
                ))
                .andExpect(jsonPath("$.code").value(
                        AuthErrorCode.INVALID_CREDENTIALS.code()
                ));
    }

    @Test
    void shouldReturnAllFieldErrorsWhenRequestValidationFails() throws Exception {
        mockMvc.perform(post("/test/errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(CommonErrorCode.PARAMETER_INVALID.code()))
                .andExpect(jsonPath("$.message").value(CommonErrorCode.PARAMETER_INVALID.message()))
                .andExpect(jsonPath("$.data.title", containsInAnyOrder(
                        "任务标题不能为空",
                        "任务标题至少需要3个字符"
                )));
    }

    @Test
    void shouldRejectMalformedJsonRequestBody() throws Exception {
        mockMvc.perform(post("/test/errors/request-body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.REQUEST_BODY_INVALID.code()))
                .andExpect(jsonPath("$.message").value(CommonErrorCode.REQUEST_BODY_INVALID.message()));
    }

    @Test
    void shouldRejectMissingOrInvalidRequestParameter() throws Exception {
        mockMvc.perform(get("/test/errors/required-parameter"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.PARAMETER_INVALID.code()));

        mockMvc.perform(get("/test/errors/typed-parameter")
                        .param("page", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.PARAMETER_INVALID.code()));
    }

    @Test
    void shouldRejectUnsupportedRequestMethod() throws Exception {
        mockMvc.perform(post("/test/errors/business"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.METHOD_NOT_ALLOWED.code()));
    }

    @Test
    void shouldRejectUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/test/errors/content-type")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("plain text"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.MEDIA_TYPE_NOT_SUPPORTED.code()));
    }

    @Test
    void shouldRejectOversizedUpload() throws Exception {
        mockMvc.perform(post("/test/errors/oversized-upload"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.PAYLOAD_TOO_LARGE.code()));
    }

    @Test
    void shouldReportUnavailableWhenAsyncExecutorRejectsTask()
            throws Exception {
        mockMvc.perform(get("/test/errors/task-rejected"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(
                        CommonErrorCode.SERVICE_UNAVAILABLE.code()
                ));
    }

    @Test
    void shouldHideInternalDetailsWhenUnknownExceptionOccurs() throws Exception {
        mockMvc.perform(get("/test/errors/unknown"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(CommonErrorCode.INTERNAL_ERROR.code()))
                .andExpect(jsonPath("$.message").value(CommonErrorCode.INTERNAL_ERROR.message()))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(not(containsString("INTERNAL_IMPLEMENTATION_DETAIL"))));
    }

    @RestController
    @RequestMapping("/test/errors")
    static class TestController {

        @GetMapping("/business")
        void throwBusinessException() {
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
        }

        @GetMapping("/unauthorized")
        void throwUnauthorizedBusinessException() {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        @PostMapping("/validation")
        void validateRequest(@Valid @RequestBody TestRequest request) {
            // 请求通过校验时无需执行操作；本测试只观察校验失败产生的响应。
        }

        @PostMapping("/request-body")
        void readRequestBody(@RequestBody TestRequest request) {
            // 方法体不是测试目标；格式错误的 JSON 无法进入该方法。
        }

        @GetMapping("/required-parameter")
        void readRequiredParameter(@RequestParam Long page) {
            // 方法体不是测试目标；缺少必填参数时无法进入该方法。
        }

        @GetMapping("/typed-parameter")
        void readTypedParameter(@RequestParam Long page) {
            // 方法体不是测试目标；参数类型转换失败时无法进入该方法。
        }

        @PostMapping(value = "/content-type", consumes = MediaType.APPLICATION_JSON_VALUE)
        void requireJsonContentType(@RequestBody TestRequest request) {
            // 方法体不是测试目标；不支持的 Content-Type 会在调用前被拒绝。
        }

        @PostMapping("/oversized-upload")
        void throwUploadTooLargeException() {
            throw new MaxUploadSizeExceededException(10L);
        }

        @GetMapping("/task-rejected")
        void throwTaskRejectedException() {
            throw new TaskRejectedException("executor busy");
        }

        @GetMapping("/unknown")
        void throwUnknownException() {
            throw new IllegalStateException("INTERNAL_IMPLEMENTATION_DETAIL");
        }
    }

    record TestRequest(
            @NotBlank(message = "任务标题不能为空")
            @Size(min = 3, message = "任务标题至少需要3个字符")
            String title
    ) {
    }

}
