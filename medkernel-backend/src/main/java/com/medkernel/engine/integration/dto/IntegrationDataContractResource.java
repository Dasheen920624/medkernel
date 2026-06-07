package com.medkernel.engine.integration.dto;

/**
 * 一个 canonical 资源类型的外部接入契约。
 */
public record IntegrationDataContractResource(
    String resourceType,
    String payloadKey,
    boolean array,
    IntegrationDataContractJsonSchema jsonSchema) {
}
