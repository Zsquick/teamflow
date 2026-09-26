package com.teamflow.core.importjob.error;

import com.teamflow.common.error.ErrorCode;

/** CSV 导入使用的稳定业务错误码。 */
public enum ImportJobErrorCode implements ErrorCode {
    IMPORT_JOB_NOT_FOUND(
            "IMPORT_JOB_0001",
            "导入任务不存在或无权访问",
            404
    ),
    INVALID_FILE_TYPE(
            "IMPORT_JOB_0002",
            "只允许上传 CSV 文件",
            400
    ),
    EMPTY_FILE(
            "IMPORT_JOB_0003",
            "CSV 文件不能为空",
            400
    ),
    FILE_TOO_LARGE(
            "IMPORT_JOB_0004",
            "CSV 文件超过允许上限",
            413
    ),
    FILE_SIZE_MISMATCH(
            "IMPORT_JOB_0005",
            "CSV 文件实际大小与声明大小不一致",
            400
    ),
    STATUS_CONFLICT(
            "IMPORT_JOB_0006",
            "导入任务已启动或已经结束",
            409
    ),
    PROJECT_NOT_ACTIVE(
            "IMPORT_JOB_0007",
            "归档项目不能导入任务",
            409
    );

    private final String code;
    private final String message;
    private final int httpStatus;

    ImportJobErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }
}
