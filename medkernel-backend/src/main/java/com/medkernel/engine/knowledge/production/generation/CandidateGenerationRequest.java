package com.medkernel.engine.knowledge.production.generation;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.medkernel.engine.knowledge.production.KnowledgeDomain;
import com.medkernel.engine.knowledge.production.TargetPipeline;

/**
 * 从受控来源生成统一资产草稿请求（AIK-STD-04）。
 *
 * <p>从一个解析后的来源版本，按申报的资产类型清单逐类生成候选；知识进入知识候选审核链，
 * 规则和路径进入统一资产版本草稿。目标管道与领域显式申报，供双形态隔离、分流与审核归口。
 * {@code domain} 为生产领域 {@link KnowledgeDomain}。
 */
public record CandidateGenerationRequest(
    @NotNull Long sourceVersionId,
    @NotNull TargetPipeline targetPipeline,
    @NotNull KnowledgeDomain domain,
    @NotEmpty @Valid List<GenerationItem> items
) {
}
