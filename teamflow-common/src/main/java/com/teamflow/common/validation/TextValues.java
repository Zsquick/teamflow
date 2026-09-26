package com.teamflow.common.validation;

import java.util.Objects;

/**
 * 提供无业务归属的文本校验和规范化操作。
 *
 * <p>本类只收纳已被多个类共同使用的基础规则。项目短标识、任务长度、
 * 用户名和令牌等业务或协议规则，仍由所属类的私有方法负责。</p>
 */
public final class TextValues {

    private TextValues() {
    }

    /**
     * 要求文本非 {@code null} 且至少包含一个非空白字符。
     *
     * @param value 待校验文本
     * @param fieldName 用于异常信息的字段名称
     * @return 未改动的原文本
     */
    public static String requireNonBlank(String value, String fieldName) {
        String validFieldName = requireFieldName(fieldName);
        Objects.requireNonNull(value, validFieldName + "不能为 null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    validFieldName + "不能为空白字符串"
            );
        }
        return value;
    }

    /**
     * 校验可选文本：{@code null} 表示未提供，非 {@code null} 时不允许空白。
     *
     * @param value 可选文本
     * @param fieldName 用于异常信息的字段名称
     * @return {@code null} 或未改动的原文本
     */
    public static String requireOptionalNonBlank(
            String value,
            String fieldName
    ) {
        String validFieldName = requireFieldName(fieldName);
        return value == null
                ? null
                : requireNonBlank(value, validFieldName);
    }

    /**
     * 去除可选文本首尾空白，并把规范化后的空文本转换为 {@code null}。
     *
     * @param value 可选文本
     * @return 规范化文本，未提供或只含空白时返回 {@code null}
     */
    public static String stripToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String requireFieldName(String fieldName) {
        Objects.requireNonNull(fieldName, "字段名称不能为 null");
        if (fieldName.isBlank()) {
            throw new IllegalArgumentException("字段名称不能为空白字符串");
        }
        return fieldName;
    }
}
