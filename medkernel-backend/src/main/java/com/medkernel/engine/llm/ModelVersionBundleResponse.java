package com.medkernel.engine.llm;

import java.time.Instant;

/**
 * 提示词、工具和模型版本组合响应。
 */
public record ModelVersionBundleResponse(
    Long id,
    String tenantId,
    String capabilityCode,
    String promptVersion,
    String promptHash,
    String toolVersion,
    String toolHash,
    String modelVersion,
    String modelHash,
    String status,
    Instant effectiveAt,
    Instant retiredAt
) {

    public static ModelVersionBundleResponse from(ModelVersionBundle bundle) {
        return new ModelVersionBundleResponse(
            bundle.id(),
            bundle.tenantId(),
            bundle.capabilityCode(),
            bundle.promptVersion(),
            bundle.promptHash(),
            bundle.toolVersion(),
            bundle.toolHash(),
            bundle.modelVersion(),
            bundle.modelHash(),
            bundle.status(),
            bundle.effectiveAt(),
            bundle.retiredAt());
    }

    public static ModelVersionBundleResponse activeFrom(ModelVersionBundle bundle) {
        return new ModelVersionBundleResponse(
            bundle.id(),
            bundle.tenantId(),
            bundle.capabilityCode(),
            bundle.promptVersion(),
            bundle.promptHash(),
            bundle.toolVersion(),
            bundle.toolHash(),
            bundle.modelVersion(),
            bundle.modelHash(),
            "ACTIVE",
            bundle.effectiveAt(),
            null);
    }
}
