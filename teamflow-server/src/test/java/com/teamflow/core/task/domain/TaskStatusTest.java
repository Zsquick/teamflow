package com.teamflow.core.task.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 任务看板状态迁移规则测试。 */
class TaskStatusTest {

    @Test
    void shouldAllowIdempotentAndAdjacentTransitionsInBothDirections() {
        assertAll(
                () -> assertTrue(TaskStatus.TODO.canTransitionTo(TaskStatus.TODO)),
                () -> assertTrue(TaskStatus.TODO.canTransitionTo(TaskStatus.IN_PROGRESS)),
                () -> assertTrue(TaskStatus.IN_PROGRESS.canTransitionTo(TaskStatus.TODO)),
                () -> assertTrue(TaskStatus.IN_PROGRESS.canTransitionTo(TaskStatus.IN_PROGRESS)),
                () -> assertTrue(TaskStatus.IN_PROGRESS.canTransitionTo(TaskStatus.DONE)),
                () -> assertTrue(TaskStatus.DONE.canTransitionTo(TaskStatus.IN_PROGRESS)),
                () -> assertTrue(TaskStatus.DONE.canTransitionTo(TaskStatus.DONE))
        );
    }

    @Test
    void shouldRejectSkippingAcrossBoardColumns() {
        assertAll(
                () -> assertFalse(TaskStatus.TODO.canTransitionTo(TaskStatus.DONE)),
                () -> assertFalse(TaskStatus.DONE.canTransitionTo(TaskStatus.TODO))
        );
    }

    @Test
    void shouldRejectMissingTargetStatus() {
        for (TaskStatus status : TaskStatus.values()) {
            assertThrows(
                    NullPointerException.class,
                    () -> status.canTransitionTo(null)
            );
        }
    }
}
