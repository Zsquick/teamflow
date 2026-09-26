package com.teamflow.common.api;

import com.teamflow.common.error.CommonErrorCode;
import com.teamflow.common.error.ErrorCode;
import com.teamflow.common.validation.TextValues;

import java.util.Objects;

/**
 * 所有 REST 接口共同使用的响应结构。
 *
 * @param code 业务状态码
 * @param message 面向调用方的提示信息
 * @param data 实际响应数据
 * @param <T> 响应数据类型
 */
public record ApiResponse<T>(String code, String message, T data) {

    /**
     * 校验所有统一响应都必须满足的基本约束。
     */
    public ApiResponse {
        TextValues.requireNonBlank(code, "业务状态码");
        TextValues.requireNonBlank(message, "响应消息");
    }

    /**
     * 创建成功响应。
     *
     * @param data 实际响应数据
     * @param <T> 响应数据类型
     * @return 统一成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                CommonErrorCode.SUCCESS.code(),
                CommonErrorCode.SUCCESS.message(),
                data
        );
    }

    /**
     * 根据错误码创建失败响应。
     *
     * @param errorCode 业务错误码
     * @param <T> 响应数据类型
     * @return 统一失败响应
     */
    public static <T> ApiResponse<T> failure(ErrorCode errorCode) {
        return failure(errorCode, null);
    }

    /**
     * 根据错误码和错误详情创建失败响应。
     *
     * @param errorCode 业务错误码
     * @param data 可安全返回给调用方的错误详情
     * @param <T> 错误详情类型
     * @return 统一失败响应
     */
    public static <T> ApiResponse<T> failure(ErrorCode errorCode, T data) {
        Objects.requireNonNull(errorCode, "业务错误码不能为 null");

        int httpStatus = errorCode.httpStatus();
        if (httpStatus < 400 || httpStatus > 599) {
            throw new IllegalArgumentException("失败响应只能使用 HTTP 4xx 或 5xx 对应的业务错误码");
        }

        return new ApiResponse<>(errorCode.code(), errorCode.message(), data);
    }
}
