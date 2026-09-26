package com.teamflow.core.project.error;

import com.teamflow.common.error.ErrorCode;

/** 项目管理使用的稳定业务错误码。 */
public enum ProjectErrorCode implements ErrorCode {
    PROJECT_NOT_FOUND("PROJECT_0001", "项目不存在或无权访问", 404),
    PROJECT_KEY_ALREADY_EXISTS("PROJECT_0002", "团队内项目短标识已存在", 409),
    INVALID_STATUS_TRANSITION("PROJECT_0003", "项目状态不允许这样变更", 409),
    PROJECT_VERSION_CONFLICT("PROJECT_0004", "项目版本已发生变化，请刷新后重试", 409);

    private final String code;
    private final String message;
    private final int httpStatus;

    ProjectErrorCode(String code, String message, int httpStatus) {
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
