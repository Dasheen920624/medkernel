package com.medkernel.engine.embed;

/**
 * 使用令牌成功校验并交换会话上下文的响应数据契约 (GA-ENG-API-11)。
 */
public record EmbedLaunchContextResponse(
    String userId,
    String roleCode,
    String tenantId,
    String patientId,
    String encounterId,
    String triggerPoint,
    boolean active,
    String traceId,
    EmbedIntegrationMode integrationMode,
    String hook,
    String hookInstance,
    EmbedModelStatus modelStatus,
    EmbedConnectionStatus connectionStatus,
    String cdsHookVersion
) {
    public EmbedLaunchContextResponse(
            String userId,
            String roleCode,
            String tenantId,
            String patientId,
            String encounterId,
            String triggerPoint,
            boolean active,
            String traceId) {
        this(userId, roleCode, tenantId, patientId, encounterId, triggerPoint, active, traceId,
            EmbedIntegrationMode.IFRAME, triggerPoint, traceId, EmbedModelStatus.MODEL_DISABLED,
            EmbedConnectionStatus.CONNECTED, "1.0");
    }
}
