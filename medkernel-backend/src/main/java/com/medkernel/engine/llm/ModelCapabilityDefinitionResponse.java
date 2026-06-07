package com.medkernel.engine.llm;

/**
 * 平台模型能力目录响应。
 */
public record ModelCapabilityDefinitionResponse(
    String capabilityCode,
    String displayName,
    String description,
    String category,
    boolean enabled,
    Integer sortOrder
) {
    static ModelCapabilityDefinitionResponse from(ModelCapabilityDefinition definition) {
        return new ModelCapabilityDefinitionResponse(
            definition.capabilityCode(),
            definition.displayName(),
            definition.description(),
            definition.category(),
            definition.enabled(),
            definition.sortOrder()
        );
    }
}
