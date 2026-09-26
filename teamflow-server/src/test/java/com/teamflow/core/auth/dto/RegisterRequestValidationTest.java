package com.teamflow.core.auth.dto;

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

/** 注册请求密码规则测试。 */
class RegisterRequestValidationTest {

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
    void shouldAcceptPasswordAtUtf8ByteBoundary() {
        assertTrue(passwordViolations("A".repeat(72)).isEmpty());
        assertTrue(passwordViolations("密".repeat(24)).isEmpty());
        assertTrue(passwordViolations("😀".repeat(18)).isEmpty());
    }

    @Test
    void shouldRejectPasswordBeyondUtf8ByteBoundary() {
        assertSinglePasswordError(
                "A".repeat(73),
                "密码使用 UTF-8 编码后不能超过 72 字节"
        );
        assertSinglePasswordError(
                "密".repeat(25),
                "密码使用 UTF-8 编码后不能超过 72 字节"
        );
        assertSinglePasswordError(
                "😀".repeat(19),
                "密码使用 UTF-8 编码后不能超过 72 字节"
        );
    }

    @Test
    void shouldCountMinimumLengthByUnicodeCodePoint() {
        assertSinglePasswordError(
                "1234567",
                "密码至少需要 8 个字符"
        );
        assertSinglePasswordError(
                "😀".repeat(4),
                "密码至少需要 8 个字符"
        );
    }

    @Test
    void shouldLetNotBlankConstraintHandleNullPassword() {
        assertSinglePasswordError(null, "密码不能为空");
        assertSinglePasswordError("        ", "密码不能为空");
    }

    @Test
    void shouldNotTrimOrNormalizePassword() {
        String password = " abcdef ";
        RegisterRequest request = validRequest(password);

        assertTrue(validator.validate(request).isEmpty());
        assertEquals(password, request.password());
    }

    private Set<ConstraintViolation<RegisterRequest>> passwordViolations(
            String password
    ) {
        return validator.validate(validRequest(password));
    }

    private RegisterRequest validRequest(String password) {
        return new RegisterRequest(
                "zhou",
                "zhou@example.com",
                password,
                "小周"
        );
    }

    private void assertSinglePasswordError(
            String password,
            String expectedMessage
    ) {
        Set<ConstraintViolation<RegisterRequest>> violations =
                passwordViolations(password);

        assertEquals(1, violations.size());
        assertEquals(
                expectedMessage,
                violations.iterator().next().getMessage()
        );
    }
}
