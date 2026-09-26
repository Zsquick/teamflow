package com.teamflow.core.task.error;

import com.teamflow.common.error.ErrorCode;

/** 任务看板使用的稳定业务错误码。 */
public enum TaskErrorCode implements ErrorCode {
    TASK_NOT_FOUND("TASK_0001", "任务不存在或无权访问", 404),
    ASSIGNEE_NOT_TEAM_MEMBER(
            "TASK_0002",
            "任务负责人必须是项目所属团队的成员",
            409
    ),
    INVALID_STATUS_TRANSITION(
            "TASK_0003",
            "任务状态不允许这样变更",
            409
    ),
    TASK_VERSION_CONFLICT(
            "TASK_0004",
            "任务版本已发生变化，请刷新后重试",
            409
    ),
    PROJECT_NOT_ACTIVE(
            "TASK_0005",
            "归档项目不允许变更任务",
            409
    );

    private final String code;
    private final String message;
    private final int httpStatus;

    TaskErrorCode(String code, String message, int httpStatus) {
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
