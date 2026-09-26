package com.teamflow.core.project.dto;

import com.teamflow.core.project.domain.ProjectStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 项目创建和修改请求 DTO 的输入边界测试。 */
class ProjectRequestValidationTest {

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
    void shouldAcceptValidRequestsAtBoundaries() {
        assertTrue(validator.validate(new CreateProjectRequest(
                "tm" + "1".repeat(30),
                "项",
                "a" + "1".repeat(15),
                "说".repeat(1000)
        )).isEmpty());
        assertTrue(validator.validate(new CreateProjectRequest(
                "tm001",
                "TeamFlow 服务端",
                "tf-api-2",
                null
        )).isEmpty());
        assertTrue(validator.validate(new UpdateProjectRequest(
                "项目",
                null,
                ProjectStatus.ACTIVE,
                0
        )).isEmpty());
    }

    @Test
    void shouldRejectInvalidTeamId() {
        assertHasViolationOn(
                validator.validate(new CreateProjectRequest(
                        " ", "项目", "AB", null
                )),
                "teamId"
        );
        assertHasViolationOn(
                validator.validate(new CreateProjectRequest(
                        "team001", "项目", "AB", null
                )),
                "teamId"
        );
        assertHasViolationOn(
                validator.validate(new CreateProjectRequest(
                        "tm" + "1".repeat(31), "项目", "AB", null
                )),
                "teamId"
        );
    }

    @Test
    void shouldRejectBlankOrOversizedProjectName() {
        assertHasViolationOn(
                validator.validate(new CreateProjectRequest(
                        "tm001", " ", "AB", null
                )),
                "name"
        );
        assertHasViolationOn(
                validator.validate(new CreateProjectRequest(
                        "tm001", "项".repeat(101), "AB", null
                )),
                "name"
        );
        assertHasViolationOn(
                validator.validate(new UpdateProjectRequest(
                        " ", null, ProjectStatus.ACTIVE, 0
                )),
                "name"
        );
        assertHasViolationOn(
                validator.validate(new UpdateProjectRequest(
                        "项".repeat(101), null, ProjectStatus.ACTIVE, 0
                )),
                "name"
        );
    }

    @Test
    void shouldRejectInvalidProjectKey() {
        for (String projectKey : new String[]{
                " ",
                "A",
                "A" + "1".repeat(16),
                "1A",
                "AB-",
                "AB--CD",
                "AB_CD"
        }) {
            assertHasViolationOn(
                    validator.validate(new CreateProjectRequest(
                            "tm001", "项目", projectKey, null
                    )),
                    "projectKey"
            );
        }
    }

    @Test
    void shouldRejectOversizedDescription() {
        String description = "说".repeat(1001);

        assertHasViolationOn(
                validator.validate(new CreateProjectRequest(
                        "tm001", "项目", "AB", description
                )),
                "description"
        );
        assertHasViolationOn(
                validator.validate(new UpdateProjectRequest(
                        "项目", description, ProjectStatus.ACTIVE, 0
                )),
                "description"
        );
    }

    @Test
    void shouldRejectMissingStatusOrInvalidExpectedVersion() {
        assertHasViolationOn(
                validator.validate(new UpdateProjectRequest(
                        "项目", null, null, 0
                )),
                "status"
        );
        assertHasViolationOn(
                validator.validate(new UpdateProjectRequest(
                        "项目", null, ProjectStatus.ACTIVE, null
                )),
                "version"
        );
        assertHasViolationOn(
                validator.validate(new UpdateProjectRequest(
                        "项目", null, ProjectStatus.ACTIVE, -1
                )),
                "version"
        );
    }

    private static void assertHasViolationOn(
            Set<? extends ConstraintViolation<?>> violations,
            String property
    ) {
        assertTrue(
                violations.stream().anyMatch(violation ->
                        violation.getPropertyPath().toString().equals(property)
                )
        );
    }
}
