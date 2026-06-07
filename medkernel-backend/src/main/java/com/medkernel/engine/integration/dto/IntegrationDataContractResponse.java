package com.medkernel.engine.integration.dto;

import java.util.List;
import java.util.Map;

/**
 * 第三方数据接入契约响应。契约随 packageVersion 固定，字段来自上下文字段目录单一真相源。
 */
public record IntegrationDataContractResponse(
    String contractId,
    String packageVersion,
    String schemaVersion,
    List<String> accessGuide,
    Map<String, IntegrationDataContractResource> resources,
    List<IntegrationDataContractField> fields) {
}
