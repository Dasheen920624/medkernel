package com.medkernel.engine.knowledge.parsing;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.medkernel.engine.knowledge.production.KnowledgeDomain;
import com.medkernel.engine.knowledge.production.TargetPipeline;
import com.medkernel.engine.knowledge.production.generation.CandidateGenerationRequest;
import com.medkernel.engine.knowledge.production.generation.GenerationItem;

/**
 * 院内上传解析后的候选生成计划。
 *
 * <p>上传入口固定生成到院内覆盖管道 {@link TargetPipeline#TENANT_OVERLAY}，调用方只声明领域与物化目标。
 */
public record DocumentUploadGenerationRequest(
    @NotNull KnowledgeDomain domain,
    @NotEmpty @Valid List<GenerationItem> items
) {
    public DocumentUploadGenerationRequest {
        items = items == null ? null : List.copyOf(items);
    }

    CandidateGenerationRequest toCandidateGenerationRequest(Long sourceVersionId) {
        return new CandidateGenerationRequest(sourceVersionId, TargetPipeline.TENANT_OVERLAY, domain, items);
    }
}
