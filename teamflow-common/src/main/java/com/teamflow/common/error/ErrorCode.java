package com.teamflow.common.error;

/**
 * 业务错误码的统一抽象。
 */
public interface ErrorCode {

    /**
     * 获取稳定、可供程序判断的业务状态码。
     *
     * @return 业务状态码
     */
    String code();

    /**
     * 获取默认错误信息。
     *
     * @return 默认错误信息
     */
    String message();

    /**
     * 获取该业务结果对应的 HTTP 状态码。
     *
     * <p>业务状态码用于前端或调用方精确判断业务结果，HTTP 状态码用于表达本次
     * HTTP 请求在协议层面的处理结果。二者职责不同，因此需要分别提供。</p>
     *
     * <p>这里使用 {@code int}，而不是 Spring 的 {@code HttpStatus}，避免通用模块
     * 依赖 Web 框架。服务端的全局异常处理器可以在响应时再把整数转换成 Spring
     * MVC 所需的 HTTP 状态。</p>
     *
     * @return 100 到 599 之间的 HTTP 状态码
     */
    int httpStatus();
}
