package com.teamflow.core.comment.error;

import com.teamflow.common.error.ErrorCode;

/** 任务评论使用的稳定业务错误码。 */
public enum CommentErrorCode implements ErrorCode {
    COMMENT_NOT_FOUND(
            "COMMENT_0001",
            "评论不存在或无权访问",
            404
    ),
    COMMENT_DELETE_FORBIDDEN(
            "COMMENT_0002",
            "只能删除自己发表的评论",
            403
    );

    private final String code;
    private final String message;
    private final int httpStatus;

    CommentErrorCode(String code, String message, int httpStatus) {
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
