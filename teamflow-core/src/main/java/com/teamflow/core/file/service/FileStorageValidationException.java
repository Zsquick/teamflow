package com.teamflow.core.file.service;

import java.io.IOException;
import java.util.Objects;

/**
 * 表示文件内容未通过可预期的存储校验，而不是磁盘本身发生故障。
 */
public class FileStorageValidationException extends IOException {

    /** 调用方可稳定识别的校验失败原因。 */
    public enum Reason {
        TOO_LARGE,
        SIZE_MISMATCH
    }

    private final Reason reason;

    public FileStorageValidationException(
            Reason reason,
            String message
    ) {
        super(Objects.requireNonNull(message, "异常消息不能为 null"));
        this.reason = Objects.requireNonNull(
                reason,
                "存储校验失败原因不能为 null"
        );
    }

    public Reason reason() {
        return reason;
    }
}
