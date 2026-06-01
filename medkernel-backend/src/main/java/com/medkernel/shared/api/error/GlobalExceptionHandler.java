package com.medkernel.shared.api.error;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import com.medkernel.shared.api.ApiError;
import com.medkernel.shared.context.RequestContext;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

/**
 * 全局异常响应翻译器。
 *
 * <p>映射策略：
 * <table>
 *   <tr><th>异常</th><th>错误码</th><th>HTTP</th></tr>
 *   <tr><td>{@link ApiException}</td><td>异常自带</td><td>由 ErrorCode 决定</td></tr>
 *   <tr><td>{@link MethodArgumentNotValidException}（{@code @Valid} 失败）</td><td>{@link ErrorCode#VALIDATION_FAILED}</td><td>400</td></tr>
 *   <tr><td>{@link ConstraintViolationException}（路径 / 查询参数校验失败）</td><td>{@link ErrorCode#VALIDATION_FAILED}</td><td>400</td></tr>
 *   <tr><td>{@link HttpMessageNotReadableException}（JSON 损坏）</td><td>{@link ErrorCode#BAD_REQUEST}</td><td>400</td></tr>
 *   <tr><td>{@link MissingServletRequestParameterException}</td><td>{@link ErrorCode#BAD_REQUEST}</td><td>400</td></tr>
 *   <tr><td>{@link MethodArgumentTypeMismatchException}</td><td>{@link ErrorCode#BAD_REQUEST}</td><td>400</td></tr>
 *   <tr><td>{@link AuthenticationException}</td><td>{@link ErrorCode#UNAUTHORIZED}</td><td>401</td></tr>
 *   <tr><td>{@link PermissionDeniedException}</td><td>PERMISSION_DENIED</td><td>403</td></tr>
 *   <tr><td>{@link AccessDeniedException}</td><td>PERMISSION_DENIED</td><td>403</td></tr>
 *   <tr><td>{@link NoHandlerFoundException}</td><td>{@link ErrorCode#NOT_FOUND}</td><td>404</td></tr>
 *   <tr><td>{@link HttpRequestMethodNotSupportedException}</td><td>{@link ErrorCode#METHOD_NOT_ALLOWED}</td><td>405</td></tr>
 *   <tr><td>{@link HttpMediaTypeNotSupportedException}</td><td>{@link ErrorCode#UNSUPPORTED_MEDIA_TYPE}</td><td>415</td></tr>
 *   <tr><td>{@link DataIntegrityViolationException}</td><td>{@link ErrorCode#CONFLICT}</td><td>409</td></tr>
 *   <tr><td>未捕获 {@link Throwable}</td><td>{@link ErrorCode#INTERNAL_ERROR}</td><td>500</td></tr>
 * </table>
 *
 * <p>所有错误响应都包含 traceId，便于客户端反馈和服务端日志关联。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String DATA_INTEGRITY_CONFLICT_MESSAGE =
        "数据约束冲突，请检查唯一字段或引用关系后重试";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
        log.debug("ApiException: code={} message={}", ex.errorCode().code(), ex.getMessage());
        return problemResponse(ex.errorCode(), ex.getMessage(), ex.fieldErrors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiError> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(this::toApiError)
            .collect(Collectors.toList());
        return problemResponse(
            ErrorCode.VALIDATION_FAILED,
            ErrorCode.VALIDATION_FAILED.defaultMessage(),
            errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        List<ApiError> errors = ex.getConstraintViolations().stream()
            .map(this::toApiError)
            .collect(Collectors.toList());
        return problemResponse(
            ErrorCode.VALIDATION_FAILED,
            ErrorCode.VALIDATION_FAILED.defaultMessage(),
            errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleBodyUnreadable(HttpMessageNotReadableException ex) {
        log.debug("Request body unreadable: {}", ex.getMessage());
        return problemResponse(ErrorCode.BAD_REQUEST, "请求体格式错误，无法解析");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParam(MissingServletRequestParameterException ex) {
        String message = "缺少必填参数 " + ex.getParameterName();
        return problemResponse(ErrorCode.BAD_REQUEST, message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "参数 " + ex.getName() + " 类型错误";
        return problemResponse(ErrorCode.BAD_REQUEST, message);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuth(AuthenticationException ex) {
        log.debug("AuthenticationException: {}", ex.getMessage());
        return problemResponse(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.defaultMessage());
    }

    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<ProblemDetail> handlePermissionDenied(PermissionDeniedException ex) {
        log.debug("PermissionDeniedException: requiredPermission={}", ex.requiredPermission());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(permissionDeniedProblem(
                ex.getMessage(),
                ex.requiredPermission(),
                ex.permissionScope(),
                ex.applyUrl()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        log.debug("AccessDeniedException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(permissionDeniedProblem(
                "权限不足：UNKNOWN",
                "UNKNOWN",
                "当前操作",
                "/security/request-access"));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoHandler(NoHandlerFoundException ex) {
        return problemResponse(ErrorCode.NOT_FOUND, "接口不存在：" + ex.getRequestURL());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return problemResponse(ErrorCode.METHOD_NOT_ALLOWED, "不支持的方法 " + ex.getMethod());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return problemResponse(
            ErrorCode.UNSUPPORTED_MEDIA_TYPE,
            ErrorCode.UNSUPPORTED_MEDIA_TYPE.defaultMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.debug("DataIntegrityViolationException: {}", ex.getMessage());
        return problemResponse(ErrorCode.CONFLICT, DATA_INTEGRITY_CONFLICT_MESSAGE);
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ProblemDetail> handleAny(Throwable ex) {
        log.error("Unhandled exception", ex);
        return problemResponse(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage());
    }

    private ApiError toApiError(FieldError fe) {
        return new ApiError(fe.getField(), fe.getCode(), fe.getDefaultMessage());
    }

    private ApiError toApiError(ConstraintViolation<?> cv) {
        return new ApiError(cv.getPropertyPath().toString(),
            cv.getConstraintDescriptor() != null
                ? cv.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()
                : "ConstraintViolation",
            cv.getMessage());
    }

    private ProblemDetail permissionDeniedProblem(
            String detail,
            String requiredPermission,
            String permissionScope,
            String applyUrl) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setType(URI.create("urn:medkernel:error:PERMISSION_DENIED"));
        problem.setTitle("权限不足");
        problem.setDetail(detail);
        problem.setProperty("code", "PERMISSION_DENIED");
        problem.setProperty("errorClass", ErrorCode.ErrorClass.AUTH.name());
        problem.setProperty("retryable", false);
        problem.setProperty("requiredPermission", requiredPermission);
        problem.setProperty("permissionScope", permissionScope);
        problem.setProperty("applyUrl", applyUrl);
        problem.setProperty("traceId", RequestContext.snapshot().traceId());
        return problem;
    }

    private ResponseEntity<ProblemDetail> problemResponse(ErrorCode code, String detail) {
        return problemResponse(code, detail, null);
    }

    private ResponseEntity<ProblemDetail> problemResponse(ErrorCode code, String detail, List<ApiError> errors) {
        ProblemDetail problem = ProblemDetail.forStatus(code.httpStatus());
        problem.setType(URI.create("urn:medkernel:error:" + code.code()));
        problem.setTitle(code.defaultMessage());
        problem.setDetail(detail == null || detail.isBlank() ? code.defaultMessage() : detail);
        problem.setProperty("code", code.code());
        problem.setProperty("errorClass", code.errorClass().name());
        problem.setProperty("retryable", code.retryable());
        problem.setProperty("traceId", RequestContext.snapshot().traceId());
        if (errors != null && !errors.isEmpty()) {
            problem.setProperty("errors", List.copyOf(errors));
        }
        return ResponseEntity.status(code.httpStatus()).body(problem);
    }
}
