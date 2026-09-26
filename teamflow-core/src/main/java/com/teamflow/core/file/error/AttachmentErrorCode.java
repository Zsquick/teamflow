package com.teamflow.core.file.error;

import com.teamflow.common.error.ErrorCode;

/** 任务附件使用的稳定业务错误码。 */
public enum AttachmentErrorCode implements ErrorCode {
    ATTACHMENT_NOT_FOUND(
            "ATTACHMENT_0001",
            "附件不存在或无权访问",
            404
    ),
    ATTACHMENT_DELETE_FORBIDDEN(
            "ATTACHMENT_0002",
            "只能由上传者或团队管理员删除附件",
            403
    ),
    ATTACHMENT_EMPTY(
            "ATTACHMENT_0003",
            "附件内容不能为空",
            400
    ),
    PROJECT_NOT_ACTIVE(
            "ATTACHMENT_0004",
            "归档项目不允许变更附件",
            409
    ),
    ATTACHMENT_TOO_LARGE(
            "ATTACHMENT_0005",
            "附件大小超过允许上限",
            413
    ),
    ATTACHMENT_SIZE_MISMATCH(
            "ATTACHMENT_0006",
            "附件实际大小与声明大小不一致",
            400
    ),
    ATTACHMENT_METADATA_INVALID(
            "ATTACHMENT_0007",
            "附件文件名或媒体类型不合法",
            400
    );

    private final String code;
    private final String message;
    private final int httpStatus;

    AttachmentErrorCode(String code, String message, int httpStatus) {
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
