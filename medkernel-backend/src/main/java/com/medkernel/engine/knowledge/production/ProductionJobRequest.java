package com.medkernel.engine.knowledge.production;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 建知识生产 job 请求（AIK-STD-13，FR-1）。
 *
 * @param sourceScope 来源范围描述（如探索 run / 料源批次引用），非空
 * @param assetType 产出资产类型，非空
 * @param producer 生产器，非空
 * @param targetPipeline 目标管道（平台主源 / 院内覆盖），非空——双形态隔离锚点
 * @param domain 生产领域（临床/药学/术语报告/评估医保/通用），非空，须显式申报
 * @param modelStrategy 模型策略标识（B0/MANUAL 可空）
 */
public record ProductionJobRequest(
    @NotBlank String sourceScope,
    @NotNull VersionedAssetType assetType,
    @NotNull KnowledgeProducer producer,
    @NotNull TargetPipeline targetPipeline,
    @NotNull KnowledgeDomain domain,
    String modelStrategy
) {
}
