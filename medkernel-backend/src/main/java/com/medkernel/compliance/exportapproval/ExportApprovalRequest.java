package com.medkernel.compliance.exportapproval;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * SYS-06 敏感数据导出申请。
 */
public record ExportApprovalRequest(
    @NotBlank @Size(max = 128) String resourceType,
    @NotEmpty Map<String, Object> exportScope,
    @NotBlank @Size(max = 512) String reason,
    @NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9._-]+") String idempotencyKey
) {
}
