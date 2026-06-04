package com.medkernel.engine.knowledge.diagnosis;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 新增诊断测试病例请求：发现集（逗号分隔标准编码）→ 期望候选/置信，作为发布门禁回归集。 */
public record DiagnosisTestCaseRequest(
    @NotBlank String caseCode,
    @NotBlank String findings,
    @NotNull Long expectedIdentityId,
    @NotNull DiagnosisConfidence expectedConfidence
) {}
