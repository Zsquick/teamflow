package com.teamflow.api.error;

import com.teamflow.common.api.ApiResponse;
import com.teamflow.common.error.BusinessException;
import com.teamflow.common.error.CommonErrorCode;
import com.teamflow.common.error.ErrorCode;
import com.teamflow.ratelimit.RateLimitExceededException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将控制器调用过程中抛出的异常转换成统一 REST 响应。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return failureResponse(errorCode);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimitExceeded(
            RateLimitExceededException exception
    ) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity
                .status(errorCode.httpStatus())
                .header(
                        HttpHeaders.RETRY_AFTER,
                        Long.toString(exception.retryAfterSeconds())
                )
                .body(ApiResponse.failure(errorCode));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> handleValidationException(
            MethodArgumentNotValidException exception
    ) {
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();

        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            String message = fieldError.getDefaultMessage();
            if (message == null || message.isBlank()) {
                message = "参数值不合法";
            }

            fieldErrors
                    .computeIfAbsent(fieldError.getField(), ignored -> new ArrayList<>())
                    .add(message);
        }

        ErrorCode errorCode = CommonErrorCode.PARAMETER_INVALID;
        return ResponseEntity
                .status(errorCode.httpStatus())
                .body(ApiResponse.failure(errorCode, fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableRequestBody() {
        return failureResponse(CommonErrorCode.REQUEST_BODY_INVALID);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class,
            HandlerMethodValidationException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequestParameter() {
        return failureResponse(CommonErrorCode.PARAMETER_INVALID);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedRequestMethod() {
        return failureResponse(CommonErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType() {
        return failureResponse(CommonErrorCode.MEDIA_TYPE_NOT_SUPPORTED);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge() {
        return failureResponse(CommonErrorCode.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(TaskRejectedException.class)
    public ResponseEntity<ApiResponse<Void>> handleTaskRejected() {
        return failureResponse(CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler({
            CannotGetJdbcConnectionException.class,
            QueryTimeoutException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleDatabaseUnavailable(
            RuntimeException exception
    ) {
        log.warn("数据库连接或查询暂时不可用", exception);
        return failureResponse(CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler({
            MissingServletRequestPartException.class,
            MultipartException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleInvalidMultipartRequest() {
        return failureResponse(CommonErrorCode.REQUEST_BODY_INVALID);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknownException(Exception exception) {
        log.error("发生未处理的服务器异常", exception);

        return failureResponse(CommonErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<ApiResponse<Void>> failureResponse(ErrorCode errorCode) {
        ResponseEntity.BodyBuilder response = ResponseEntity
                .status(errorCode.httpStatus());
        if (errorCode.httpStatus() == 401) {
            response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        return response.body(ApiResponse.failure(errorCode));
    }
}
