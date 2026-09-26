package com.teamflow.core.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 校验可选时间是否为固定三位毫秒的 UTC 文本。 */
@Documented
@Constraint(validatedBy = CanonicalUtcTimeValidator.class)
@Target({
        ElementType.FIELD,
        ElementType.METHOD,
        ElementType.PARAMETER,
        ElementType.ANNOTATION_TYPE,
        ElementType.TYPE_USE,
        ElementType.RECORD_COMPONENT
})
@Retention(RetentionPolicy.RUNTIME)
public @interface CanonicalUtcTime {

    String message() default
            "时间必须使用 yyyy-MM-dd'T'HH:mm:ss.SSS'Z' 格式";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
