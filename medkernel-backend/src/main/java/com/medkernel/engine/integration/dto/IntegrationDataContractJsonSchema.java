package com.medkernel.engine.integration.dto;

import java.util.List;
import java.util.Map;

/**
 * 资源级 JSON Schema 风格契约。
 */
public record IntegrationDataContractJsonSchema(
    String type,
    String title,
    Map<String, IntegrationDataContractFieldSchema> properties,
    List<String> required) {
}
