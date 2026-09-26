package com.teamflow.core.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Objects;

/**
 * 统一生成和校验数据库使用的 UTC 时间文本。
 */
public final class UtcTimeText {

    private static final int CANONICAL_TEXT_LENGTH = 24;
    private static final DateTimeFormatter FORMATTER =
            new DateTimeFormatterBuilder()
                    .appendInstant(3)
                    .toFormatter(Locale.ROOT);

    private UtcTimeText() {
    }

    /**
     * 将时间格式化为固定三位毫秒的 UTC 文本。
     *
     * @param instant 待格式化时间
     * @return 例如 {@code 2026-09-07T10:20:30.123Z}
     */
    public static String format(Instant instant) {
        String result = FORMATTER.format(
                Objects.requireNonNull(instant, "时间不能为 null")
        );
        if (result.length() != CANONICAL_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "时间必须能够表示为 yyyy-MM-dd'T'HH:mm:ss.SSS'Z' 格式"
            );
        }
        return result;
    }

    /**
     * 校验并原样返回 UTC 时间文本。
     *
     * @param value 时间文本
     * @param fieldName 用于异常信息的字段名称
     * @return 原时间文本
     */
    public static String requireValid(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + "不能为 null");
        if (value.length() != CANONICAL_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    fieldName + "必须使用 yyyy-MM-dd'T'HH:mm:ss.SSS'Z' 格式"
            );
        }
        try {
            Instant parsed = Instant.from(FORMATTER.parse(value));
            if (!FORMATTER.format(parsed).equals(value)) {
                throw new IllegalArgumentException(
                        fieldName + "必须使用 yyyy-MM-dd'T'HH:mm:ss.SSS'Z' 格式"
                );
            }
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    fieldName + "必须使用 yyyy-MM-dd'T'HH:mm:ss.SSS'Z' 格式",
                    exception
            );
        }
        return value;
    }

    /**
     * 要求时间文本不早于指定下界。
     *
     * <p>两个参数都会先通过规范 UTC 格式校验。由于格式固定且统一使用 UTC，
     * 字典序与时间先后顺序一致，因此无需在领域实体中重复解析。</p>
     *
     * @param value 待校验时间文本
     * @param lowerBound 允许的最早时间文本
     * @param fieldName 待校验字段名称
     * @param lowerBoundFieldName 下界字段名称
     * @return 已校验的原时间文本
     */
    public static String requireAtOrAfter(
            String value,
            String lowerBound,
            String fieldName,
            String lowerBoundFieldName
    ) {
        String validValue = requireValid(value, fieldName);
        String validLowerBound = requireValid(
                lowerBound,
                lowerBoundFieldName
        );
        if (validValue.compareTo(validLowerBound) < 0) {
            throw new IllegalArgumentException(
                    fieldName + "不能早于" + lowerBoundFieldName
            );
        }
        return validValue;
    }

    /**
     * 根据可注入时钟生成当前 UTC 时间文本。
     *
     * @param clock 系统或测试时钟
     * @return 当前 UTC 时间文本
     */
    public static String now(Clock clock) {
        return format(Objects.requireNonNull(clock, "时钟不能为 null").instant());
    }
}
