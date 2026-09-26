package com.teamflow.core.common.validation;

import com.teamflow.core.common.time.UtcTimeText;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** {@link CanonicalUtcTime} 的校验实现。 */
public final class CanonicalUtcTimeValidator
        implements ConstraintValidator<CanonicalUtcTime, String> {

    @Override
    public boolean isValid(
            String value,
            ConstraintValidatorContext context
    ) {
        if (value == null) {
            return true;
        }
        try {
            UtcTimeText.requireValid(value, "时间");
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
