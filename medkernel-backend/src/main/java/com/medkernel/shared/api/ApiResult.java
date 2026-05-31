package com.medkernel.shared.api;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.RequestContext;

/**
 * MedKernel v1.0 GA · GA-ENG-BASE-03 标准 API 响应包络。
 *
 * <p>所有成功 REST 端点的返回值都必须使用本类型包装；失败响应统一走 RFC 7807
 * {@link org.springframework.http.ProblemDetail}，不得再返回本类型的失败包络。
 *
 * <p>JSON 形态（成功）：
 * <pre>{@code
 * {
 *   "success": true,
 *   "code": "OK",
 *   "message": "操作成功",
 *   "data": { ... },
 *   "traceId": "8c4e1e2f-...",
 *   "timestamp": "2026-05-25T10:23:45.123Z"
 * }
 * }</pre>
 * @param <T> 业务数据类型；无响应体的成功结果为 null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResult<T>(
    boolean success,
    String code,
    String message,
    T data,
    String traceId,
    Instant timestamp
) {

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(true, ErrorCode.OK.code(), ErrorCode.OK.defaultMessage(),
            data, RequestContext.currentTraceId(), Instant.now());
    }

    public static <T> ApiResult<T> ok(T data, String message) {
        return new ApiResult<>(true, ErrorCode.OK.code(), message,
            data, RequestContext.currentTraceId(), Instant.now());
    }

    public static <T> ApiResult<T> empty() {
        return ok(null);
    }
}
