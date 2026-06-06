package com.medkernel.compliance.exportapproval;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * SYS-06 敏感数据导出审批请求。
 */
public record ExportApprovalReviewRequest(
    @NotNull ExportApprovalDecision decision,
    @NotBlank @Size(max = 512) String comment,
    Long expectedVersion
) {
}
