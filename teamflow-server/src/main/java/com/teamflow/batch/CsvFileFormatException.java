package com.teamflow.batch;

/** 表示 CSV 文件整体结构错误；此类错误不能按单行跳过。 */
public class CsvFileFormatException extends RuntimeException {

    public CsvFileFormatException(String message) {
        super(message);
    }

    public CsvFileFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
