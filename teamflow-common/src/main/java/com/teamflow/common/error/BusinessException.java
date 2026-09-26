package com.teamflow.common.error;

import java.util.Objects;

/**
 * 表示请求合法但业务规则不允许继续执行的异常。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * 使用业务错误码创建异常。
     *
     * @param errorCode 业务错误码
     */
    public BusinessException(ErrorCode errorCode) {
        super(Objects.requireNonNull(errorCode, "业务错误码不能为 null").message());
        this.errorCode = errorCode;
    }

    /**
     * 获取异常对应的业务错误码。
     *
     * @return 业务错误码
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
