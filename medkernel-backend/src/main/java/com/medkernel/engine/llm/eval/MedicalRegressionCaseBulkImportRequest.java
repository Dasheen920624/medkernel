package com.medkernel.engine.llm.eval;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * 医学回归基准用例批量导入请求。
 */
public record MedicalRegressionCaseBulkImportRequest(
    @NotEmpty @Size(max = 100) List<@Valid MedicalRegressionCaseRequest> cases
) {
}
