package com.teamflow.core.task.error;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 任务管理错误码协议测试。 */
class TaskErrorCodeTest {

    @Test
    void shouldExposeStableMappings() {
        assertMapping(TaskErrorCode.TASK_NOT_FOUND,
                "TASK_0001", "任务不存在或无权访问", 404);
        assertMapping(TaskErrorCode.ASSIGNEE_NOT_TEAM_MEMBER,
                "TASK_0002", "任务负责人必须是项目所属团队的成员", 409);
        assertMapping(TaskErrorCode.INVALID_STATUS_TRANSITION,
                "TASK_0003", "任务状态不允许这样变更", 409);
        assertMapping(TaskErrorCode.TASK_VERSION_CONFLICT,
                "TASK_0004", "任务版本已发生变化，请刷新后重试", 409);
        assertMapping(TaskErrorCode.PROJECT_NOT_ACTIVE,
                "TASK_0005", "归档项目不允许变更任务", 409);
    }

    @Test
    void shouldKeepEveryTaskBusinessCodeUniqueAndNamespaced() {
        Set<String> codes = Arrays.stream(TaskErrorCode.values())
                .map(TaskErrorCode::code)
                .collect(Collectors.toSet());

        assertEquals(TaskErrorCode.values().length, codes.size());
        assertTrue(codes.stream().allMatch(code -> code.startsWith("TASK_")));
    }

    private static void assertMapping(
            TaskErrorCode errorCode,
            String code,
            String message,
            int httpStatus
    ) {
        assertEquals(code, errorCode.code());
        assertEquals(message, errorCode.message());
        assertEquals(httpStatus, errorCode.httpStatus());
    }
}
