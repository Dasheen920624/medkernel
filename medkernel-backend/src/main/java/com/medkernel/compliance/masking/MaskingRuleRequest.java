package com.medkernel.compliance.masking;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * SYS-06 脱敏规则写入请求。
 */
public record MaskingRuleRequest(
    @NotBlank @Size(max = 128) String resourceType,
    @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}", message = "字段名仅允许字母、数字和下划线")
        String fieldName,
    @Size(max = 64) String scenarioCode,
    @NotNull MaskingStrategy strategy,
    @NotBlank @Size(max = 4) String maskChar,
    @NotNull @Min(0) @Max(32) Integer prefixKeep,
    @NotNull @Min(0) @Max(32) Integer suffixKeep,
    @NotNull MaskingRuleStatus status,
    @NotBlank @Size(max = 512) String reason,
    Long expectedVersion
) {
}
