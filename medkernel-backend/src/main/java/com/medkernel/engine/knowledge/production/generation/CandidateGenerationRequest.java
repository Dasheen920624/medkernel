package com.medkernel.engine.knowledge.production.generation;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.medkernel.engine.knowledge.production.KnowledgeDomain;
import com.medkernel.engine.knowledge.production.TargetPipeline;

/**
 * 从受控来源生成知识候选请求（AIK-STD-04）。
 *
 * <p>从一个解析后的来源版本，按申报的资产类型清单逐类生成候选；目标管道与领域显式申报，
 * 供双形态隔离与会签路由。{@code domain} 为路由领域 {@link KnowledgeDomain}（与模板的医学领域维正交）。
 */
public record CandidateGenerationRequest(
    @NotNull Long sourceVersionId,
    @NotNull TargetPipeline targetPipeline,
    @NotNull KnowledgeDomain domain,
    @NotEmpty @Valid List<GenerationItem> items
) {
}
