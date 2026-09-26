package com.teamflow.batch;

/** 表示某一数据行不合法，可由 Spring Batch 的 skip 策略处理。 */
public class CsvRowValidationException extends RuntimeException {

    private final long rowNumber;

    public CsvRowValidationException(long rowNumber, String message) {
        super("CSV 第 " + rowNumber + " 行：" + message);
        if (rowNumber < 2) {
            throw new IllegalArgumentException("CSV 数据行号必须从 2 开始");
        }
        this.rowNumber = rowNumber;
    }

    public CsvRowValidationException(
            long rowNumber,
            String message,
            Throwable cause
    ) {
        super("CSV 第 " + rowNumber + " 行：" + message, cause);
        if (rowNumber < 2) {
            throw new IllegalArgumentException("CSV 数据行号必须从 2 开始");
        }
        this.rowNumber = rowNumber;
    }

    public long rowNumber() {
        return rowNumber;
    }
}
