package com.medkernel.engine.integration.dto;

/**
 * JSON Schema 风格字段定义；只表达接入形状，不承载患者数据。
 */
public record IntegrationDataContractFieldSchema(
    String type,
    String title,
    String description,
    String unit,
    String codeSystem,
    boolean required,
    boolean derived) {
}
