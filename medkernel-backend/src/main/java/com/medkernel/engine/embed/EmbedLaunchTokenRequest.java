package com.medkernel.engine.embed;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 嵌入启动令牌生成请求数据契约 (GA-ENG-API-11)。
 */
public record EmbedLaunchTokenRequest(
    @NotBlank String userId,
    @NotBlank String roleCode,
    @NotBlank String patientId,
    @NotBlank String encounterId,
    @NotBlank String triggerPoint,
    Integer expireSeconds,
    EmbedIntegrationMode integrationMode,
    String hook,
    String hookInstance
) {
    public EmbedLaunchTokenRequest {
        integrationMode = EmbedIntegrationMode.defaultIfNull(integrationMode);
        hook = hook == null || hook.isBlank() ? triggerPoint : hook;
    }

    public EmbedLaunchTokenRequest(
            String userId,
            String roleCode,
            String patientId,
            String encounterId,
            String triggerPoint,
            Integer expireSeconds) {
        this(userId, roleCode, patientId, encounterId, triggerPoint, expireSeconds,
            EmbedIntegrationMode.IFRAME, triggerPoint, null);
    }
}
