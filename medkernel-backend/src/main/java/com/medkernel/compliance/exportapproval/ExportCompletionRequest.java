package com.medkernel.compliance.exportapproval;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * SYS-06 敏感数据真实导出完成登记请求。
 */
public record ExportCompletionRequest(
    @NotBlank @Size(max = 512) String exportUri,
    @NotBlank @Pattern(regexp = "sm3:[0-9a-fA-F]{64}") String exportDigest,
    @NotBlank @Size(max = 512) String reason,
    Long expectedVersion
) {
}
