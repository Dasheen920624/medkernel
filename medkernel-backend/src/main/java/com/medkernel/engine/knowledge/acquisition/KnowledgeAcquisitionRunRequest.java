package com.medkernel.engine.knowledge.acquisition;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;

import com.medkernel.engine.knowledge.parsing.DocumentFormat;

/**
 * 公域资料获取请求。URL 必须命中已审批来源允许清单，内容进入既有文档解析链路；如声明生成计划，
 * 解析出的来源版本会继续进入统一候选生成/审核链。
 */
public record KnowledgeAcquisitionRunRequest(
    @NotBlank String sourceCode,
    @NotBlank String url,
    @NotBlank String versionNo,
    @NotNull DocumentFormat format,
    @Valid AcquisitionCandidateGenerationRequest generation
) {
}
