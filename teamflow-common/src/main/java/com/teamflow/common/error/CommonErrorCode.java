package com.teamflow.common.error;

import com.teamflow.common.validation.TextValues;

/**
 * 跨业务模块共同使用的基础结果码。
 *
 * <p>业务码采用 {@code COMMON_四位序号}，不把 HTTP 状态编码进业务码本身。
 * 业务码用于调用方稳定识别错误，HTTP 状态用于描述本次 HTTP 交互的协议语义。</p>
 */
public enum CommonErrorCode implements ErrorCode {
    SUCCESS("COMMON_0000", "成功", 200),
    PARAMETER_INVALID("COMMON_0001", "请求参数不合法", 400),
    REQUEST_BODY_INVALID("COMMON_0002", "请求内容格式不正确", 400),
    UNAUTHORIZED("COMMON_0003", "请先登录", 401),
    FORBIDDEN("COMMON_0004", "无权执行该操作", 403),
    RESOURCE_NOT_FOUND("COMMON_0005", "请求的资源不存在", 404),
    CONFLICT("COMMON_0006", "资源状态发生冲突", 409),
    METHOD_NOT_ALLOWED("COMMON_0007", "请求方法不支持", 405),
    PAYLOAD_TOO_LARGE("COMMON_0008", "上传内容超过大小限制", 413),
    MEDIA_TYPE_NOT_SUPPORTED("COMMON_0009", "不支持的内容类型", 415),
    TOO_MANY_REQUESTS("COMMON_0010", "请求过于频繁，请稍后重试", 429),
    SERVICE_UNAVAILABLE("COMMON_9001", "服务暂时不可用，请稍后重试", 503),
    INTERNAL_ERROR("COMMON_9999", "服务器暂时无法处理请求", 500);

    private final String code;
    private final String message;
    private final int httpStatus;

    /**
     * 创建错误码枚举项。
     *
     * @param code 稳定的业务状态码
     * @param message 可以安全返回给调用方的默认提示信息
     * @param httpStatus 推荐使用的 HTTP 状态码
     */
    CommonErrorCode(String code, String message, int httpStatus) {
        TextValues.requireNonBlank(code, "业务状态码");
        TextValues.requireNonBlank(message, "默认提示信息");

        if (!code.matches("COMMON_\\d{4}")) {
            throw new IllegalArgumentException("通用业务状态码必须符合 COMMON_四位数字");
        }
        if (httpStatus < 100 || httpStatus > 599) {
            throw new IllegalArgumentException("HTTP 状态码必须在 100 到 599 之间");
        }
        boolean successCode = "COMMON_0000".equals(code);
        boolean successStatus = httpStatus >= 200 && httpStatus < 300;
        if (successCode != successStatus) {
            throw new IllegalArgumentException("只有 COMMON_0000 可以映射到 2xx HTTP 状态");
        }

        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    /**
     * 获取业务状态码。
     *
     * @return 业务状态码
     */
    @Override
    public String code() {
        return code;
    }

    /**
     * 获取默认提示信息。
     *
     * @return 默认提示信息
     */
    @Override
    public String message() {
        return message;
    }

    /**
     * 获取推荐使用的 HTTP 状态码。
     *
     * @return HTTP 状态码
     */
    @Override
    public int httpStatus() {
        return httpStatus;
    }
}
