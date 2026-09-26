package com.teamflow.common.validation;

/** 提供与具体业务无关的数值边界校验。 */
public final class NumberValues {

    private NumberValues() {
    }

    /** 要求 long 数值大于或等于 0。 */
    public static long requireNonNegative(long value, String fieldName) {
        String validFieldName = TextValues.requireNonBlank(
                fieldName,
                "字段名称"
        );
        if (value < 0) {
            throw new IllegalArgumentException(
                    validFieldName + "不能小于 0"
            );
        }
        return value;
    }
}
