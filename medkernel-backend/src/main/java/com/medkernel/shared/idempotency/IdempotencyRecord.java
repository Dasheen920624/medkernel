package com.medkernel.shared.idempotency;

import java.time.Instant;

/**
 * 平台级幂等记录。
 *
 * @param tenantId            租户 ID
 * @param idempotencyKey      请求幂等键
 * @param requestHash         请求摘要
 * @param requestMethod       HTTP 方法
 * @param requestPath         请求路径
 * @param status              处理状态
 * @param responseStatus      首次成功响应状态码
 * @param responseContentType 首次成功响应媒体类型
 * @param responseBody        首次成功响应体
 * @param resultHash          首次成功响应摘要
 * @param traceId             首次成功请求 traceId
 * @param createdAt           创建时间
 * @param expiresAt           过期时间
 */
public record IdempotencyRecord(
    String tenantId,
    String idempotencyKey,
    String requestHash,
    String requestMethod,
    String requestPath,
    String status,
    Integer responseStatus,
    String responseContentType,
    String responseBody,
    String resultHash,
    String traceId,
    Instant createdAt,
    Instant expiresAt
) {

    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_COMPLETED = "COMPLETED";

    public static IdempotencyRecord processing(
            String tenantId,
            String idempotencyKey,
            String requestHash,
            String requestMethod,
            String requestPath,
            String traceId,
            Instant createdAt,
            Instant expiresAt) {
        return new IdempotencyRecord(
            tenantId,
            idempotencyKey,
            requestHash,
            requestMethod,
            requestPath,
            STATUS_PROCESSING,
            null,
            null,
            null,
            null,
            traceId,
            createdAt,
            expiresAt);
    }

    public boolean completed() {
        return STATUS_COMPLETED.equals(status);
    }

    public IdempotencyRecord complete(
            int responseStatus,
            String responseContentType,
            String responseBody,
            String resultHash,
            String traceId) {
        return new IdempotencyRecord(
            tenantId,
            idempotencyKey,
            requestHash,
            requestMethod,
            requestPath,
            STATUS_COMPLETED,
            responseStatus,
            responseContentType,
            responseBody,
            resultHash,
            traceId,
            createdAt,
            expiresAt);
    }
}
