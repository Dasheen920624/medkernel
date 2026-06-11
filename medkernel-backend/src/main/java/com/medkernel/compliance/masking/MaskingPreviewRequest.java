package com.medkernel.compliance.masking;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;

/**
 * SYS-06 脱敏预览请求。
 *
 * <p>租户与数据范围由服务端从当前请求上下文解析，避免调用方伪造 tenantId 或 rawAllowed 状态。
 */
public record MaskingPreviewRequest(
    @NotBlank String resourceType,
    String scenarioCode,
    Map<String, Object> values,
    List<@NotBlank String> sensitiveFields
) {

    MaskingRequest toMaskingRequest(String tenantId) {
        return new MaskingRequest(
            tenantId,
            resourceType,
            scenarioCode,
            values == null ? Map.of() : values,
            sensitiveFields == null ? List.of() : sensitiveFields
        );
    }
}
