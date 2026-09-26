package com.teamflow.core.audit.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 操作日志字段边界和不可变读取测试。 */
class OperationLogTest {

    private static final String CREATED_AT = "2026-09-18T01:02:03.456Z";

    @Test
    void shouldExposeCompleteSafeAuditSnapshot() {
        OperationLog operationLog = new OperationLog(
                "op001",
                "u001",
                "COMMENT_DELETE",
                "TASK_COMMENT",
                "c001",
                "TaskCommentController#delete;errorCode=COMMON_0006",
                false,
                12L,
                "0123456789abcdef0123456789abcdef",
                "2001:db8::1",
                CREATED_AT
        );

        assertAll(
                () -> assertEquals("op001", operationLog.getId()),
                () -> assertEquals("u001", operationLog.getUserId()),
                () -> assertEquals(
                        "COMMENT_DELETE",
                        operationLog.getAction()
                ),
                () -> assertEquals(
                        "TASK_COMMENT",
                        operationLog.getResourceType()
                ),
                () -> assertEquals("c001", operationLog.getResourceId()),
                () -> assertEquals(
                        "TaskCommentController#delete;errorCode=COMMON_0006",
                        operationLog.getDetail()
                ),
                () -> assertFalse(operationLog.isSuccess()),
                () -> assertEquals(12L, operationLog.getDurationMs()),
                () -> assertEquals(
                        "0123456789abcdef0123456789abcdef",
                        operationLog.getTraceId()
                ),
                () -> assertEquals(
                        "2001:db8::1",
                        operationLog.getIpAddress()
                ),
                () -> assertEquals(CREATED_AT, operationLog.getCreatedAt())
        );
    }

    @Test
    void shouldAllowNullableRequestAndResourceContext() {
        OperationLog operationLog = new OperationLog(
                "op001",
                null,
                "SYSTEM_ACTION",
                "SYSTEM",
                null,
                null,
                true,
                0L,
                null,
                null,
                CREATED_AT
        );

        assertAll(
                () -> assertNull(operationLog.getUserId()),
                () -> assertNull(operationLog.getResourceId()),
                () -> assertNull(operationLog.getDetail()),
                () -> assertNull(operationLog.getTraceId()),
                () -> assertNull(operationLog.getIpAddress())
        );
    }

    @Test
    void shouldRejectMissingRequiredValuesAndNegativeDuration() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> validLog(" ", "ACTION", "TASK", 0L, CREATED_AT)
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> validLog("op001", null, "TASK", 0L, CREATED_AT)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> validLog("op001", "ACTION", " ", 0L, CREATED_AT)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> validLog("op001", "ACTION", "TASK", -1L, CREATED_AT)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> validLog(
                                "op001",
                                "ACTION",
                                "TASK",
                                0L,
                                "2026-09-18 01:02:03"
                        )
                )
        );
    }

    @Test
    void shouldKeepTextLengthsAlignedWithDatabaseColumns() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> validLog("x".repeat(33), "ACTION", "TASK", 0L, CREATED_AT)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> validLog("op001", "x".repeat(65), "TASK", 0L, CREATED_AT)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> validLog("op001", "ACTION", "x".repeat(65), 0L, CREATED_AT)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new OperationLog(
                                "op001",
                                "u001",
                                "ACTION",
                                "TASK",
                                "t001",
                                "x".repeat(501),
                                true,
                                0L,
                                "trace",
                                "127.0.0.1",
                                CREATED_AT
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new OperationLog(
                                "op001",
                                "u001",
                                "ACTION",
                                "TASK",
                                "t001",
                                "detail",
                                true,
                                0L,
                                "x".repeat(65),
                                "127.0.0.1",
                                CREATED_AT
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new OperationLog(
                                "op001",
                                "u001",
                                "ACTION",
                                "TASK",
                                "t001",
                                "detail",
                                true,
                                0L,
                                "trace",
                                "x".repeat(46),
                                CREATED_AT
                        )
                )
        );
    }

    private static OperationLog validLog(
            String id,
            String action,
            String resourceType,
            long durationMs,
            String createdAt
    ) {
        return new OperationLog(
                id,
                "u001",
                action,
                resourceType,
                "t001",
                "handler#method",
                true,
                durationMs,
                "trace",
                "127.0.0.1",
                createdAt
        );
    }
}
