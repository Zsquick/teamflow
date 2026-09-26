package com.teamflow.common.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamflow.common.error.CommonErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 统一响应单元测试。 */
class ApiResponseTest {

    @Test
    void shouldCreateSuccessResponse() {
        List<String> data = List.of("TASK-1", "TASK-2");

        ApiResponse<List<String>> response = ApiResponse.success(data);

        assertAll(
                () -> assertEquals(CommonErrorCode.SUCCESS.code(), response.code()),
                () -> assertEquals(CommonErrorCode.SUCCESS.message(), response.message()),
                () -> assertEquals(data, response.data())
        );
    }

    @Test
    void shouldCreateFailureResponse() {
        ApiResponse<Void> response = ApiResponse.failure(CommonErrorCode.RESOURCE_NOT_FOUND);

        assertAll(
                () -> assertEquals(CommonErrorCode.RESOURCE_NOT_FOUND.code(), response.code()),
                () -> assertEquals(CommonErrorCode.RESOURCE_NOT_FOUND.message(), response.message()),
                () -> assertNull(response.data())
        );
    }

    @Test
    void shouldCreateFailureResponseWithDetails() {
        Map<String, List<String>> details = Map.of(
                "title", List.of("任务标题不能为空"),
                "deadline", List.of("截止时间不能早于当前时间")
        );

        ApiResponse<Map<String, List<String>>> response =
                ApiResponse.failure(CommonErrorCode.PARAMETER_INVALID, details);

        assertAll(
                () -> assertEquals(CommonErrorCode.PARAMETER_INVALID.code(), response.code()),
                () -> assertEquals(CommonErrorCode.PARAMETER_INVALID.message(), response.message()),
                () -> assertEquals(details, response.data())
        );
    }

    @Test
    void shouldRejectInvalidResponseMetadata() {
        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new ApiResponse<>(null, "成功", null)
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new ApiResponse<>("COMMON_0000", null, null)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ApiResponse<>(" ", "成功", null)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ApiResponse<>("COMMON_0000", " ", null)
                )
        );
    }

    @Test
    void shouldRejectInvalidFailureErrorCode() {
        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> ApiResponse.failure(null)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> ApiResponse.failure(CommonErrorCode.SUCCESS)
                )
        );
    }

    @Test
    void shouldSerializeRecordAsJson() throws Exception {
        ApiResponse<Map<String, Long>> response =
                ApiResponse.success(Map.of("id", 100L));
        ObjectMapper objectMapper = new ObjectMapper();

        JsonNode json = objectMapper.readTree(
                objectMapper.writeValueAsString(response)
        );

        assertAll(
                () -> assertEquals("COMMON_0000", json.get("code").asText()),
                () -> assertEquals("成功", json.get("message").asText()),
                () -> assertEquals(100L, json.get("data").get("id").asLong())
        );
    }
}
