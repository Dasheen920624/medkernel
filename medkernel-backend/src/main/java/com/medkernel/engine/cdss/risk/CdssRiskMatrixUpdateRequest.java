package com.medkernel.engine.cdss.risk;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * CDSS 风险矩阵受控变更请求。
 */
public record CdssRiskMatrixUpdateRequest(
    @NotBlank String matrixVersion,
    @NotBlank String changeReason,
    CdssRiskMatrixStatus status,
    @Valid List<CdssRiskMatrixEntryRequest> entries
) {
    public CdssRiskMatrixUpdateRequest {
        status = status == null ? CdssRiskMatrixStatus.ACTIVE : status;
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
