package com.teamflow.core.auth.validation;

import jakarta.validation.ConstraintDeclarationException;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * {@link PasswordLength} 的校验实现。
 */
public final class PasswordLengthValidator
        implements ConstraintValidator<PasswordLength, String> {

    private int minCodePoints;
    private int maxUtf8Bytes;

    @Override
    public void initialize(PasswordLength constraint) {
        minCodePoints = constraint.minCodePoints();
        maxUtf8Bytes = constraint.maxUtf8Bytes();

        if (minCodePoints < 0 || maxUtf8Bytes < 0) {
            throw new ConstraintDeclarationException("密码长度限制不能小于 0");
        }
    }

    @Override
    public boolean isValid(
            String value,
            ConstraintValidatorContext context
    ) {
        if (value == null || value.isBlank()) {
            return true;
        }

        int codePointCount = value.codePointCount(0, value.length());
        if (codePointCount < minCodePoints) {
            return violation(
                    context,
                    "密码至少需要 " + minCodePoints + " 个字符"
            );
        }

        byte[] utf8Bytes = value.getBytes(StandardCharsets.UTF_8);
        try {
            if (utf8Bytes.length > maxUtf8Bytes) {
                return violation(
                        context,
                        "密码使用 UTF-8 编码后不能超过 "
                                + maxUtf8Bytes + " 字节"
                );
            }
        } finally {
            Arrays.fill(utf8Bytes, (byte) 0);
        }

        return true;
    }

    private boolean violation(
            ConstraintValidatorContext context,
            String message
    ) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addConstraintViolation();
        return false;
    }
}
