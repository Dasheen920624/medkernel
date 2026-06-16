package com.medkernel.engine.knowledge.production.model;

import com.medkernel.engine.knowledge.production.generation.GenerationSummary;

/**
 * 模型知识生产结果。
 *
 * <p>保留模型任务与版本三元组，同时复用 AIK-STD-04 的生成汇总，便于生产中心统一展示候选、跳过与阻断。
 */
public record ModelKnowledgeProductionResult(
    String jobCode,
    String modelTaskId,
    String modelMode,
    String modelVersion,
    String promptVersion,
    String toolVersion,
    GenerationSummary summary
) {
}
