package com.teamflow.core.comment.dto;

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

/** 新增评论请求的规范化与 Validation 边界测试。 */
class CommentRequestValidationTest {

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
    void shouldNormalizeBeforeValidationAndAcceptBoundary() {
        CreateCommentRequest normalized = new CreateCommentRequest(
                "  需要跟进\n "
        );
        CreateCommentRequest boundary = new CreateCommentRequest(
                " " + "评".repeat(2000) + " "
        );

        assertEquals("需要跟进", normalized.content());
        assertEquals(2000, boundary.content().length());
        assertTrue(validator.validate(normalized).isEmpty());
        assertTrue(validator.validate(boundary).isEmpty());
    }

    @Test
    void shouldRejectNullOrBlankContent() {
        assertHasContentViolation(new CreateCommentRequest(null));
        assertHasContentViolation(new CreateCommentRequest("  \n "));
    }

    @Test
    void shouldRejectOversizedNormalizedContent() {
        assertHasContentViolation(
                new CreateCommentRequest(" " + "评".repeat(2001) + " ")
        );
    }

    private static void assertHasContentViolation(
            CreateCommentRequest request
    ) {
        Set<ConstraintViolation<CreateCommentRequest>> violations =
                validator.validate(request);
        assertTrue(
                violations.stream().anyMatch(violation ->
                        violation.getPropertyPath().toString()
                                .equals("content")
                ),
                () -> "缺少 content 的校验错误: " + violations
        );
    }
}
