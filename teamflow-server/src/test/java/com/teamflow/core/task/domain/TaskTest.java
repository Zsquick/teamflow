package com.teamflow.core.task.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 任务领域对象的不变量、规范化和版本推进测试。 */
class TaskTest {

    private static final String CREATED_AT = "2026-09-16T01:02:03.456Z";
    private static final String UPDATED_AT = "2026-09-16T02:03:04.567Z";
    private static final String DUE_AT = "2026-09-20T12:00:00.000Z";

    @Test
    void shouldCreateNormalizedTaskWithInitialBoardStateAndVersion() {
        Task task = Task.create(
                "t000001",
                "p000001",
                "  完成任务看板  ",
                "  编写服务与测试  ",
                TaskPriority.HIGH,
                "u000002",
                "u000001",
                DUE_AT,
                CREATED_AT
        );

        assertAll(
                () -> assertEquals("t000001", task.getId()),
                () -> assertEquals("p000001", task.getProjectId()),
                () -> assertEquals("完成任务看板", task.getTitle()),
                () -> assertEquals("编写服务与测试", task.getDescription()),
                () -> assertEquals(TaskStatus.TODO, task.getStatus()),
                () -> assertEquals(TaskPriority.HIGH, task.getPriority()),
                () -> assertEquals("u000002", task.getAssigneeId()),
                () -> assertEquals("u000001", task.getReporterId()),
                () -> assertEquals(DUE_AT, task.getDueAt()),
                () -> assertEquals(0, task.getVersion()),
                () -> assertEquals(CREATED_AT, task.getCreatedAt()),
                () -> assertEquals(CREATED_AT, task.getUpdatedAt())
        );
    }

    @Test
    void shouldNormalizeBlankDescriptionAndAllowOptionalAssignmentAndDueTime() {
        Task task = Task.create(
                "t000001",
                "p000001",
                "任务",
                "   ",
                TaskPriority.MEDIUM,
                null,
                "u000001",
                null,
                CREATED_AT
        );

        assertAll(
                () -> assertNull(task.getDescription()),
                () -> assertNull(task.getAssigneeId()),
                () -> assertNull(task.getDueAt())
        );
    }

    @Test
    void shouldUpdateAllMutableFieldsAndAdvanceVersionAndTimeTogether() {
        Task task = existingTask(2);

        assertTrue(task.updateDetails(
                "  已开始的任务  ",
                "   ",
                TaskStatus.IN_PROGRESS,
                TaskPriority.URGENT,
                null,
                null,
                UPDATED_AT
        ));

        assertAll(
                () -> assertEquals("已开始的任务", task.getTitle()),
                () -> assertNull(task.getDescription()),
                () -> assertEquals(TaskStatus.IN_PROGRESS, task.getStatus()),
                () -> assertEquals(TaskPriority.URGENT, task.getPriority()),
                () -> assertNull(task.getAssigneeId()),
                () -> assertNull(task.getDueAt()),
                () -> assertEquals(3, task.getVersion()),
                () -> assertEquals(UPDATED_AT, task.getUpdatedAt()),
                () -> assertEquals(CREATED_AT, task.getCreatedAt())
        );
    }

    @Test
    void shouldTreatNormalizedEquivalentDetailsAsNoOp() {
        Task task = existingTask(2);

        assertFalse(task.updateDetails(
                "  现有任务  ",
                "  现有说明  ",
                TaskStatus.TODO,
                TaskPriority.HIGH,
                "u000002",
                DUE_AT,
                UPDATED_AT
        ));

        assertAll(
                () -> assertEquals(2, task.getVersion()),
                () -> assertEquals(CREATED_AT, task.getUpdatedAt()),
                () -> assertEquals("现有任务", task.getTitle())
        );
    }

    @Test
    void shouldRejectEarlierUpdateWithoutChangingTask() {
        Task task = existingTask(2);

        assertThrows(
                IllegalArgumentException.class,
                () -> task.updateDetails(
                        "改变标题",
                        null,
                        TaskStatus.IN_PROGRESS,
                        TaskPriority.LOW,
                        null,
                        null,
                        "2026-09-15T23:59:59.999Z"
                )
        );

        assertAll(
                () -> assertEquals("现有任务", task.getTitle()),
                () -> assertEquals(TaskStatus.TODO, task.getStatus()),
                () -> assertEquals(2, task.getVersion()),
                () -> assertEquals(CREATED_AT, task.getUpdatedAt())
        );
    }

    @Test
    void shouldKeepStateWhenVersionOverflows() {
        Task task = new Task(
                "t000001", "p000001", "现有任务", null,
                TaskStatus.TODO, TaskPriority.MEDIUM, null,
                "u000001", null, Integer.MAX_VALUE,
                CREATED_AT, CREATED_AT
        );

        assertThrows(
                IllegalStateException.class,
                () -> task.updateDetails(
                        "改变标题", null, TaskStatus.IN_PROGRESS,
                        TaskPriority.HIGH, null, null, UPDATED_AT
                )
        );

        assertAll(
                () -> assertEquals("现有任务", task.getTitle()),
                () -> assertEquals(TaskStatus.TODO, task.getStatus()),
                () -> assertEquals(Integer.MAX_VALUE, task.getVersion()),
                () -> assertEquals(CREATED_AT, task.getUpdatedAt())
        );
    }

    @Test
    void shouldEnforceDatabaseLengthsInsideDomainForBatchReuse() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Task.create(
                        "t000001", "p000001", "任".repeat(201), null,
                        TaskPriority.MEDIUM, null, "u000001", null,
                        CREATED_AT
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> Task.create(
                        "t000001", "p000001", "任务", "说".repeat(5001),
                        TaskPriority.MEDIUM, null, "u000001", null,
                        CREATED_AT
                )
        );

        Task task = existingTask(2);
        assertThrows(
                IllegalArgumentException.class,
                () -> task.updateDetails(
                        "任".repeat(201), "改变说明",
                        TaskStatus.IN_PROGRESS, TaskPriority.LOW,
                        null, null, UPDATED_AT
                )
        );
        assertAll(
                () -> assertEquals("现有任务", task.getTitle()),
                () -> assertEquals("现有说明", task.getDescription()),
                () -> assertEquals(2, task.getVersion())
        );
    }

    @Test
    void shouldRejectInvalidPersistentAndOptionalValues() {
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> new Task(
                        null, "p000001", "任务", null, TaskStatus.TODO,
                        TaskPriority.LOW, null, "u000001", null, 0,
                        CREATED_AT, CREATED_AT
                )),
                () -> assertThrows(IllegalArgumentException.class, () -> new Task(
                        "t000001", "p000001", " ", null, TaskStatus.TODO,
                        TaskPriority.LOW, null, "u000001", null, 0,
                        CREATED_AT, CREATED_AT
                )),
                () -> assertThrows(IllegalArgumentException.class, () -> new Task(
                        "t000001", "p000001", "任务", null, TaskStatus.TODO,
                        TaskPriority.LOW, " ", "u000001", null, 0,
                        CREATED_AT, CREATED_AT
                )),
                () -> assertThrows(IllegalArgumentException.class, () -> new Task(
                        "t000001", "p000001", "任务", null, TaskStatus.TODO,
                        TaskPriority.LOW, null, "u000001", "2026-09-20", 0,
                        CREATED_AT, CREATED_AT
                )),
                () -> assertThrows(IllegalArgumentException.class, () -> new Task(
                        "t000001", "p000001", "任务", null, TaskStatus.TODO,
                        TaskPriority.LOW, null, "u000001", null, -1,
                        CREATED_AT, CREATED_AT
                )),
                () -> assertThrows(IllegalArgumentException.class, () -> new Task(
                        "t000001", "p000001", "任务", null, TaskStatus.TODO,
                        TaskPriority.LOW, null, "u000001", null, 0,
                        CREATED_AT, "2026-09-16T00:00:00.000Z"
                ))
        );
    }

    private Task existingTask(int version) {
        return new Task(
                "t000001",
                "p000001",
                "现有任务",
                "现有说明",
                TaskStatus.TODO,
                TaskPriority.HIGH,
                "u000002",
                "u000001",
                DUE_AT,
                version,
                CREATED_AT,
                CREATED_AT
        );
    }
}
