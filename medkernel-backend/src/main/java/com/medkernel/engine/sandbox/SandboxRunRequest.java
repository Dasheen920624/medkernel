package com.medkernel.engine.sandbox;

import java.time.Instant;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.medkernel.engine.embed.EmbedIntegrationMode;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 沙盘运行请求。第一阶段支持标准上下文快照入口，并允许业务系统提交标准资源覆盖值。
 */
public record SandboxRunRequest(
    String entryMode,
    JsonNode contextOverride,
    Instant occurredAt,
    String parentOrigin,
    EmbedIntegrationMode integrationMode,
    SandboxRunMode mode,
    String replayCaseId
) {

    public SandboxRunRequest {
        entryMode = entryMode == null || entryMode.isBlank()
            ? "SNAPSHOT"
            : entryMode.trim().toUpperCase(Locale.ROOT);
        if (!"SNAPSHOT".equals(entryMode)) {
            throw badRequest("第一阶段沙盘仅支持 SNAPSHOT 数据入口");
        }
        parentOrigin = parentOrigin == null || parentOrigin.isBlank() ? null : parentOrigin.trim();
        integrationMode = EmbedIntegrationMode.defaultIfNull(integrationMode);
        mode = mode == null ? SandboxRunMode.CURRENT : mode;
        replayCaseId = replayCaseId == null || replayCaseId.isBlank() ? null : replayCaseId.trim();
        if (mode == SandboxRunMode.CURRENT && replayCaseId != null) {
            throw badRequest("CURRENT 运行不得绑定历史重放清单");
        }
        if (mode != SandboxRunMode.CURRENT && replayCaseId == null) {
            throw badRequest("历史原样重放或对比必须提供 replayCaseId");
        }
        if (mode != SandboxRunMode.CURRENT && contextOverride != null) {
            throw badRequest("历史原样重放或对比必须使用清单内脱敏上下文");
        }
    }

    public SandboxRunRequest(
            String entryMode,
            JsonNode contextOverride,
            Instant occurredAt,
            String parentOrigin,
            EmbedIntegrationMode integrationMode) {
        this(entryMode, contextOverride, occurredAt, parentOrigin, integrationMode, SandboxRunMode.CURRENT, null);
    }

    public SandboxRunRequest(
            String entryMode,
            JsonNode contextOverride,
            Instant occurredAt,
            String parentOrigin) {
        this(
            entryMode, contextOverride, occurredAt, parentOrigin,
            EmbedIntegrationMode.IFRAME, SandboxRunMode.CURRENT, null);
    }

    private static ApiException badRequest(String message) {
        return new ApiException(ErrorCode.BAD_REQUEST, message);
    }
}
