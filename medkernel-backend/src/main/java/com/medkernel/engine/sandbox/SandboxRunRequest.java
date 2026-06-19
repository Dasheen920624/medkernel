package com.medkernel.engine.sandbox;

import java.time.Instant;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.medkernel.engine.embed.EmbedIntegrationMode;

/**
 * 沙盘运行请求。第一阶段支持标准上下文快照入口，并允许业务系统提交标准资源覆盖值。
 */
public record SandboxRunRequest(
    String entryMode,
    JsonNode contextOverride,
    Instant occurredAt,
    String parentOrigin,
    EmbedIntegrationMode integrationMode,
    SandboxRunMode mode
) {

    public SandboxRunRequest {
        entryMode = entryMode == null || entryMode.isBlank()
            ? "SNAPSHOT"
            : entryMode.trim().toUpperCase(Locale.ROOT);
        if (!"SNAPSHOT".equals(entryMode)) {
            throw new IllegalArgumentException("第一阶段沙盘仅支持 SNAPSHOT 数据入口");
        }
        parentOrigin = parentOrigin == null || parentOrigin.isBlank() ? null : parentOrigin.trim();
        integrationMode = EmbedIntegrationMode.defaultIfNull(integrationMode);
        mode = mode == null ? SandboxRunMode.CURRENT : mode;
    }

    public SandboxRunRequest(
            String entryMode,
            JsonNode contextOverride,
            Instant occurredAt,
            String parentOrigin,
            EmbedIntegrationMode integrationMode) {
        this(entryMode, contextOverride, occurredAt, parentOrigin, integrationMode, SandboxRunMode.CURRENT);
    }

    public SandboxRunRequest(
            String entryMode,
            JsonNode contextOverride,
            Instant occurredAt,
            String parentOrigin) {
        this(
            entryMode, contextOverride, occurredAt, parentOrigin,
            EmbedIntegrationMode.IFRAME, SandboxRunMode.CURRENT);
    }
}
