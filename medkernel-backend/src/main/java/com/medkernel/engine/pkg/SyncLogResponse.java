package com.medkernel.engine.pkg;

/**
 * 知识包同步发布日志响应 DTO。
 */
public record SyncLogResponse(
    String logId,
    String planId,
    String adapterId,
    SyncLogStatus status,
    String errorCode,
    String errorMessage,
    int retryCount,
    String syncEvidence
) {
    public static SyncLogResponse from(SyncLog entity) {
        return new SyncLogResponse(
            entity.logId(),
            entity.planId(),
            entity.adapterId(),
            entity.status(),
            entity.errorCode(),
            entity.errorMessage(),
            entity.retryCount(),
            entity.syncEvidence()
        );
    }
}
