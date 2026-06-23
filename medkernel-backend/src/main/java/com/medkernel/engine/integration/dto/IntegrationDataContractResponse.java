package com.medkernel.engine.integration.dto;

import java.util.List;
import java.util.Map;

/**
 * 第三方数据接入契约响应。服务端锁定医院当前运行修订，字段来自上下文字段目录单一真相源。
 */
public record IntegrationDataContractResponse(
    String contractId,
    String runtimeReleaseId,
    String schemaVersion,
    List<String> accessGuide,
    Map<String, IntegrationDataContractResource> resources,
    List<IntegrationDataContractField> fields) {
}
