package com.medkernel.compliance.masking;

import java.util.List;
import java.util.Map;

/**
 * SYS-06 后端脱敏执行请求。
 */
public record MaskingRequest(
    String tenantId,
    String resourceType,
    String scenarioCode,
    Map<String, Object> values,
    List<String> sensitiveFields
) {
}
