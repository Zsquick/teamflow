package com.teamflow.core.notification.error;

import com.teamflow.common.error.ErrorCode;

/** 站内通知使用的稳定业务错误码。 */
public enum NotificationErrorCode implements ErrorCode {
    NOTIFICATION_NOT_FOUND(
            "NOTIFICATION_0001",
            "通知不存在或无权访问",
            404
    );

    private final String code;
    private final String message;
    private final int httpStatus;

    NotificationErrorCode(String code, String message, int httpStatus) {
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
