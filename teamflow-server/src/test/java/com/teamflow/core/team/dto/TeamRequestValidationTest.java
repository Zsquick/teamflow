package com.teamflow.core.team.dto;

import com.teamflow.core.team.domain.TeamRole;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 团队和成员请求 DTO 的输入边界测试。 */
class TeamRequestValidationTest {

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
    void shouldAcceptValidTeamAndInvitationRequestsAtBoundaries() {
        assertTrue(validator.validate(
                new CreateTeamRequest("研", "说".repeat(500))
        ).isEmpty());
        assertTrue(validator.validate(
                new CreateTeamInvitationRequest(
                        "zhoushuai@example.com",
                        TeamRole.MEMBER
                )
        ).isEmpty());
        assertTrue(validator.validate(
                new InvitationPreviewRequest("zhoushuai")
        ).isEmpty());
        assertTrue(validator.validate(
                new UpdateTeamMemberRoleRequest(TeamRole.ADMIN)
        ).isEmpty());
    }

    @Test
    void shouldRejectBlankOrOversizedTeamName() {
        assertHasViolationOn(
                validator.validate(new CreateTeamRequest(" ", null)),
                "name"
        );
        assertHasViolationOn(
                validator.validate(
                        new CreateTeamRequest("团".repeat(65), null)
                ),
                "name"
        );
    }

    @Test
    void shouldRejectOversizedDescription() {
        assertHasViolationOn(
                validator.validate(
                        new CreateTeamRequest("研发团队", "说".repeat(501))
                ),
                "description"
        );
    }

    @Test
    void shouldRejectInvalidInvitationIdentifier() {
        assertHasViolationOn(
                validator.validate(
                        new CreateTeamInvitationRequest(" ", TeamRole.MEMBER)
                ),
                "identifier"
        );
        assertHasViolationOn(
                validator.validate(
                        new CreateTeamInvitationRequest(
                        "u".repeat(129),
                        TeamRole.MEMBER
                )),
                "identifier"
        );
        assertHasViolationOn(
                validator.validate(new InvitationPreviewRequest(" ")),
                "identifier"
        );
    }

    @Test
    void shouldRejectMissingRequestedRoles() {
        assertHasViolationOn(
                validator.validate(new CreateTeamInvitationRequest(
                        "zhoushuai", null)),
                "role"
        );
        assertHasViolationOn(
                validator.validate(new UpdateTeamMemberRoleRequest(null)),
                "role"
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
