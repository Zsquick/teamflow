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

/** 登录与刷新请求的输入边界测试。 */
class AuthRequestValidationTest {

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
    void shouldAcceptValidLoginRequest() {
        assertTrue(validator.validate(
                new LoginRequest("zhou@example.com", "password")
        ).isEmpty());
    }

    @Test
    void shouldRejectBlankOrOversizedIdentifier() {
        assertSingleMessage(
                validator.validate(new LoginRequest(" ", "password")),
                "登录标识不能为空"
        );
        assertSingleMessage(
                validator.validate(new LoginRequest("u".repeat(129), "password")),
                "登录标识不能超过 128 个字符"
        );
    }

    @Test
    void shouldLetNotBlankAloneHandleBlankLoginPassword() {
        assertSingleMessage(
                validator.validate(new LoginRequest("zhou", " ")),
                "密码不能为空"
        );
    }

    @Test
    void shouldRejectLoginPasswordBeyondBcryptUtf8Limit() {
        assertSingleMessage(
                validator.validate(new LoginRequest("zhou", "密".repeat(25))),
                "密码使用 UTF-8 编码后不能超过 72 字节"
        );
    }

    @Test
    void shouldNotApplyRegistrationMinimumLengthDuringLogin() {
        assertTrue(validator.validate(
                new LoginRequest("zhou", "x")
        ).isEmpty());
    }

    @Test
    void shouldAcceptRefreshTokenAtMaximumLength() {
        assertTrue(validator.validate(
                new RefreshTokenRequest("r".repeat(4096))
        ).isEmpty());
    }

    @Test
    void shouldRejectBlankOrOversizedRefreshToken() {
        assertSingleMessage(
                validator.validate(new RefreshTokenRequest(" ")),
                "刷新令牌不能为空"
        );
        assertSingleMessage(
                validator.validate(new RefreshTokenRequest("r".repeat(4097))),
                "刷新令牌不能超过 4096 个字符"
        );
    }

    private void assertSingleMessage(
            Set<? extends ConstraintViolation<?>> violations,
            String expectedMessage
    ) {
        assertEquals(1, violations.size());
        assertEquals(
                expectedMessage,
                violations.iterator().next().getMessage()
        );
    }
}
