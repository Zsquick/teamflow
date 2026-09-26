package com.teamflow.core.common.version;

import com.teamflow.common.validation.TextValues;

/** 统一处理领域实体的乐观锁版本号。 */
public final class Versions {

    private Versions() {
    }

    /**
     * 校验从持久层还原的版本号。
     *
     * @param version 版本号
     * @param fieldName 用于异常信息的字段名称
     * @return 已校验的版本号
     */
    public static int requireNonNegative(int version, String fieldName) {
        String validFieldName = TextValues.requireNonBlank(
                fieldName,
                "字段名称"
        );
        if (version < 0) {
            throw new IllegalArgumentException(
                    validFieldName + "不能小于 0"
            );
        }
        return version;
    }

    /**
     * 使用精确加法计算下一个版本号，防止整数溢出回绕为负数。
     *
     * @param currentVersion 当前版本号
     * @param fieldName 用于异常信息的字段名称
     * @return 下一个版本号
     */
    public static int next(int currentVersion, String fieldName) {
        requireNonNegative(currentVersion, fieldName);
        try {
            return Math.addExact(currentVersion, 1);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException(
                    fieldName + "已达到整数上限",
                    exception
            );
        }
    }
}
