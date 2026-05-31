package com.medkernel.shared.api.error;

import java.util.List;

import com.medkernel.shared.api.ApiError;

/**
 * 业务异常基类。任何业务层判定的非法情形都应抛出本异常或其子类，
 * 由 {@link GlobalExceptionHandler} 统一翻译为 {@link org.springframework.http.ProblemDetail}。
 *
 * <p>禁止在业务代码中直接抛 {@link RuntimeException} 或 {@link IllegalArgumentException}；
 * 这些将被作为未预期错误兜底翻译为 {@link ErrorCode#INTERNAL_ERROR}。
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<ApiError> fieldErrors;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), null, null);
    }

    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, null, null);
    }

    public ApiException(ErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, message, null, cause);
    }

    public ApiException(ErrorCode errorCode, String message, List<ApiError> fieldErrors, Throwable cause) {
        super(message == null ? errorCode.defaultMessage() : message, cause);
        this.errorCode = errorCode;
        this.fieldErrors = fieldErrors == null || fieldErrors.isEmpty() ? null : List.copyOf(fieldErrors);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public ErrorCode.ErrorClass errorClass() {
        return errorCode.errorClass();
    }

    public boolean retryable() {
        return errorCode.retryable();
    }

    public List<ApiError> fieldErrors() {
        return fieldErrors;
    }

    public static ApiException notFound(String resource) {
        return new ApiException(ErrorCode.NOT_FOUND, resource + " 不存在");
    }

    public static ApiException forbidden(String reason) {
        return new ApiException(ErrorCode.FORBIDDEN, reason);
    }

    public static ApiException conflict(String reason) {
        return new ApiException(ErrorCode.CONFLICT, reason);
    }

    public static ApiException tenantMissing() {
        return new ApiException(ErrorCode.TENANT_CONTEXT_MISSING);
    }
}
