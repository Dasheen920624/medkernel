package com.medkernel.engine.llm;

/**
 * 增强接入矩阵台账项响应（LLM-05）。
 */
public record ModelEnhancementMatrixResponse(
    String businessPoint,
    String businessName,
    String capabilityCode,
    String b0Path,
    String accessStatus,
    boolean enabled,
    int sortOrder
) {

    public static ModelEnhancementMatrixResponse from(ModelEnhancementMatrix entity) {
        return new ModelEnhancementMatrixResponse(
            entity.businessPoint(),
            entity.businessName(),
            entity.capabilityCode(),
            entity.b0Path(),
            entity.accessStatus(),
            entity.enabled(),
            entity.sortOrder());
    }
}
