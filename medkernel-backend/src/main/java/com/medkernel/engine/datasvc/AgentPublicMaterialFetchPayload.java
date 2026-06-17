package com.medkernel.engine.datasvc;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.medkernel.engine.knowledge.acquisition.AcquisitionCandidateGenerationRequest;
import com.medkernel.engine.knowledge.acquisition.KnowledgeAcquisitionRunRequest;
import com.medkernel.engine.knowledge.parsing.DocumentFormat;

/**
 * Agent 受控获取公域资料工具载荷（AIK-STD-14）。
 *
 * <p>Agent 不直接抓取并落库；只声明 allowlisted 来源、HTTPS URL、版本号和文档格式，
 * 由后端公域获取编排统一执行白名单、部署形态、解析、资料库和候选生成门禁。
 */
public record AgentPublicMaterialFetchPayload(
    @NotBlank String sourceCode,
    @NotBlank String url,
    @NotBlank String versionNo,
    @NotNull DocumentFormat format,
    String dataLevel,
    @Valid AcquisitionCandidateGenerationRequest generation
) {
    KnowledgeAcquisitionRunRequest toRequest() {
        return new KnowledgeAcquisitionRunRequest(sourceCode, url, versionNo, format, generation);
    }
}
