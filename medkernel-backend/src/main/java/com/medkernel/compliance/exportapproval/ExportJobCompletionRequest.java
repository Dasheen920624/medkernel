package com.medkernel.compliance.exportapproval;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 使用后端异步任务登记敏感数据导出完成。
 */
public record ExportJobCompletionRequest(
    @NotBlank @Size(max = 128) String jobId,
    @NotBlank @Size(max = 512) String reason,
    Long expectedVersion
) {
}
