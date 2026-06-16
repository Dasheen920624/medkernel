package com.medkernel.engine.integration.dto;

/**
 * 对外数据接入字段契约条目，由 canonical 字段目录派生，供第三方系统映射。
 */
public record IntegrationDataContractField(
    String resourceType,
    String fieldPath,
    String payloadKey,
    String propertyName,
    String displayName,
    String dataType,
    String jsonSchemaType,
    String unit,
    String codeSystem,
    boolean required,
    boolean derived,
    boolean externalWritable,
    String description) {
}
