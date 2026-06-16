package com.medkernel.engine.knowledge.production.model;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.medkernel.engine.factory.AssetSourceRef;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.production.MaterializationTarget;

/**
 * 模型知识生产请求（AIK-STD-13 FR2）。
 *
 * <p>调用方必须显式给出受控来源、目标身份和生产提示；模型只补候选内容，不替调用方推断权威身份。
 */
public record ModelKnowledgeProductionRequest(
    @NotBlank String capabilityCode,
    @NotBlank String prompt,
    String providerCode,
    Integer timeoutSeconds,
    @NotBlank String assetIdentity,
    @NotBlank String subject,
    @NotEmpty List<@Valid AssetSourceRef> sources,
    @NotNull SourceAuthorityLevel trustLevel,
    @NotNull KnowledgeRiskLevel riskLevel,
    @NotNull @Valid MaterializationTarget target
) {
    public ModelKnowledgeProductionRequest {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
