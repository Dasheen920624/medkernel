package com.medkernel.engine.emrlevel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 电子病历评级证据导出请求。
 */
public record EmrLevelEvidenceExportRequest(
    @NotBlank String hospitalOrgId,
    @NotBlank String standardVersion,
    @NotBlank @Size(max = 128) String idempotencyKey
) {
}
