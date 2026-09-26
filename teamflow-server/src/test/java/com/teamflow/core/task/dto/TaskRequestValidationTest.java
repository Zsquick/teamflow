package com.teamflow.core.task.dto;

import com.teamflow.core.task.domain.TaskPriority;
import com.teamflow.core.task.domain.TaskStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 任务创建与更新请求的输入边界测试。 */
class TaskRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void shouldAcceptValidRequestsAtBoundariesAndNullableFields() {
        assertTrue(validator.validate(new CreateTaskRequest(
                "p" + "1".repeat(31),
                "任".repeat(200),
                "说".repeat(5000),
                TaskPriority.URGENT,
                "u" + "2".repeat(31),
                "2026-09-20T12:00:00.000Z"
        )).isEmpty());
        assertTrue(validator.validate(new CreateTaskRequest(
                "p001", "任务", null, TaskPriority.LOW, null, null
        )).isEmpty());
        assertTrue(validator.validate(new UpdateTaskRequest(
                "任务", null, TaskStatus.TODO, TaskPriority.MEDIUM,
                null, null, 0
        )).isEmpty());
        assertTrue(validator.validate(new ChangeTaskStatusRequest(
                TaskStatus.IN_PROGRESS,
                0
        )).isEmpty());
    }

    @Test
    void shouldRejectInvalidProjectIds() {
        for (String projectId : new String[]{
                null, " ", "project001", "p12", "p" + "1".repeat(32)
        }) {
            assertHasViolationOn(
                    validator.validate(createRequest(projectId, "任务")),
                    "projectId"
            );
        }
    }

    @Test
    void shouldRejectBlankOrOversizedTitlesForCreateAndUpdate() {
        assertHasViolationOn(
                validator.validate(createRequest("p001", " ")),
                "title"
        );
        assertHasViolationOn(
                validator.validate(createRequest("p001", "任".repeat(201))),
                "title"
        );
        assertHasViolationOn(
                validator.validate(updateRequest(" ", null, 0)),
                "title"
        );
        assertHasViolationOn(
                validator.validate(updateRequest("任".repeat(201), null, 0)),
                "title"
        );
    }

    @Test
    void shouldRejectOversizedDescriptions() {
        String description = "说".repeat(5001);

        assertHasViolationOn(
                validator.validate(new CreateTaskRequest(
                        "p001", "任务", description, TaskPriority.HIGH,
                        null, null
                )),
                "description"
        );
        assertHasViolationOn(
                validator.validate(new UpdateTaskRequest(
                        "任务", description, TaskStatus.TODO,
                        TaskPriority.HIGH, null, null, 0
                )),
                "description"
        );
    }

    @Test
    void shouldRejectMissingPriorityStatusOrInvalidVersion() {
        assertHasViolationOn(
                validator.validate(new CreateTaskRequest(
                        "p001", "任务", null, null, null, null
                )),
                "priority"
        );
        assertHasViolationOn(
                validator.validate(new UpdateTaskRequest(
                        "任务", null, null, TaskPriority.HIGH,
                        null, null, 0
                )),
                "status"
        );
        assertHasViolationOn(
                validator.validate(new UpdateTaskRequest(
                        "任务", null, TaskStatus.TODO, null,
                        null, null, 0
                )),
                "priority"
        );
        assertHasViolationOn(
                validator.validate(updateRequest("任务", null, null)),
                "version"
        );
        assertHasViolationOn(
                validator.validate(updateRequest("任务", null, -1)),
                "version"
        );
        assertHasViolationOn(
                validator.validate(new ChangeTaskStatusRequest(null, 0)),
                "status"
        );
        assertHasViolationOn(
                validator.validate(new ChangeTaskStatusRequest(
                        TaskStatus.TODO,
                        null
                )),
                "version"
        );
        assertHasViolationOn(
                validator.validate(new ChangeTaskStatusRequest(
                        TaskStatus.TODO,
                        -1
                )),
                "version"
        );
    }

    @Test
    void shouldRejectInvalidOptionalAssigneeIds() {
        for (String assigneeId : new String[]{
                " ", "user001", "u12", "u" + "1".repeat(32)
        }) {
            assertHasViolationOn(
                    validator.validate(new CreateTaskRequest(
                            "p001", "任务", null, TaskPriority.HIGH,
                            assigneeId, null
                    )),
                    "assigneeId"
            );
            assertHasViolationOn(
                    validator.validate(updateRequest("任务", assigneeId, 0)),
                    "assigneeId"
            );
        }
    }

    @Test
    void shouldRejectNonCanonicalDueTimes() {
        for (String dueAt : new String[]{
                "2026-09-20",
                "2026-09-20T12:00:00Z",
                "2026-09-20T20:00:00.000+08:00",
                "2026-02-30T12:00:00.000Z"
        }) {
            assertHasViolationOn(
                    validator.validate(new CreateTaskRequest(
                            "p001", "任务", null, TaskPriority.HIGH,
                            null, dueAt
                    )),
                    "dueAt"
            );
            assertHasViolationOn(
                    validator.validate(new UpdateTaskRequest(
                            "任务", null, TaskStatus.TODO,
                            TaskPriority.HIGH, null, dueAt, 0
                    )),
                    "dueAt"
            );
        }
    }

    private static CreateTaskRequest createRequest(
            String projectId,
            String title
    ) {
        return new CreateTaskRequest(
                projectId, title, null, TaskPriority.HIGH, null, null
        );
    }

    private static UpdateTaskRequest updateRequest(
            String title,
            String assigneeId,
            Integer version
    ) {
        return new UpdateTaskRequest(
                title, null, TaskStatus.TODO, TaskPriority.HIGH,
                assigneeId, null, version
        );
    }

    private static void assertHasViolationOn(
            Set<? extends ConstraintViolation<?>> violations,
            String property
    ) {
        assertTrue(
                violations.stream().anyMatch(violation ->
                        violation.getPropertyPath().toString().equals(property)
                ),
                () -> "缺少字段 " + property + " 的校验错误: " + violations
        );
    }
}
