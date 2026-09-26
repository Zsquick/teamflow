package com.teamflow.core.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 按 Unicode 码点下限和 UTF-8 字节上限校验密码长度。
 */
@Documented
@Constraint(validatedBy = PasswordLengthValidator.class)
@Target({
        ElementType.FIELD,
        ElementType.METHOD,
        ElementType.PARAMETER,
        ElementType.ANNOTATION_TYPE,
        ElementType.TYPE_USE,
        ElementType.RECORD_COMPONENT
})
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordLength {

    String message() default "密码长度不符合要求";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    int minCodePoints() default 0;

    int maxUtf8Bytes();
}
