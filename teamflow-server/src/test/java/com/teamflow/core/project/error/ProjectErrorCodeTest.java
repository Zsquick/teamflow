package com.teamflow.core.project.error;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 项目管理错误码协议测试。 */
class ProjectErrorCodeTest {

    @Test
    void shouldExposeStableNotFoundAndDuplicateKeyMappings() {
        assertMapping(
                ProjectErrorCode.PROJECT_NOT_FOUND,
                "PROJECT_0001",
                "项目不存在或无权访问",
                404
        );
        assertMapping(
                ProjectErrorCode.PROJECT_KEY_ALREADY_EXISTS,
                "PROJECT_0002",
                "团队内项目短标识已存在",
                409
        );
    }

    @Test
    void shouldExposeStableTransitionAndVersionConflictMappings() {
        assertMapping(
                ProjectErrorCode.INVALID_STATUS_TRANSITION,
                "PROJECT_0003",
                "项目状态不允许这样变更",
                409
        );
        assertMapping(
                ProjectErrorCode.PROJECT_VERSION_CONFLICT,
                "PROJECT_0004",
                "项目版本已发生变化，请刷新后重试",
                409
        );
    }

    @Test
    void shouldKeepEveryProjectBusinessCodeUniqueAndNamespaced() {
        Set<String> codes = Arrays.stream(ProjectErrorCode.values())
                .map(ProjectErrorCode::code)
                .collect(Collectors.toSet());

        assertEquals(ProjectErrorCode.values().length, codes.size());
        assertTrue(codes.stream().allMatch(code -> code.startsWith("PROJECT_")));
    }

    private void assertMapping(
            ProjectErrorCode errorCode,
            String code,
            String message,
            int httpStatus
    ) {
        assertEquals(code, errorCode.code());
        assertEquals(message, errorCode.message());
        assertEquals(httpStatus, errorCode.httpStatus());
    }
}
