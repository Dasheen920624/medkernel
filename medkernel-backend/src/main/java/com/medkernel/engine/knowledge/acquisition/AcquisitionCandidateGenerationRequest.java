package com.medkernel.engine.knowledge.acquisition;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.medkernel.engine.knowledge.production.KnowledgeDomain;
import com.medkernel.engine.knowledge.production.TargetPipeline;
import com.medkernel.engine.knowledge.production.generation.CandidateGenerationRequest;
import com.medkernel.engine.knowledge.production.generation.GenerationItem;

/**
 * 公域资料获取后的候选生成计划。来源版本由获取编排在解析成功后填入，目标身份仍由调用方显式声明。
 */
public record AcquisitionCandidateGenerationRequest(
    @NotNull TargetPipeline targetPipeline,
    @NotNull KnowledgeDomain domain,
    @NotEmpty @Valid List<GenerationItem> items
) {
    public AcquisitionCandidateGenerationRequest {
        items = items == null ? null : List.copyOf(items);
    }

    CandidateGenerationRequest toCandidateGenerationRequest(Long sourceVersionId) {
        return new CandidateGenerationRequest(sourceVersionId, targetPipeline, domain, items);
    }
}
