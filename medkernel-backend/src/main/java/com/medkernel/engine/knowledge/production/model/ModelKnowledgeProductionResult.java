package com.medkernel.engine.knowledge.production.model;

import com.medkernel.engine.knowledge.production.generation.GenerationSummary;
import com.medkernel.engine.llm.ModelEgressConfirmationChallenge;

/**
 * 模型知识生产结果。
 *
 * <p>保留模型任务与提示词、工具和模型版本，同时复用 AIK-STD-04 的生成汇总，便于生产中心统一展示候选、跳过与阻断。
 */
public record ModelKnowledgeProductionResult(
    String jobCode,
    String modelTaskId,
    String modelMode,
    String modelVersion,
    String promptVersion,
    String toolVersion,
    GenerationSummary summary,
    ModelEgressConfirmationChallenge egressConfirmation
) {
    public ModelKnowledgeProductionResult(
            String jobCode,
            String modelTaskId,
            String modelMode,
            String modelVersion,
            String promptVersion,
            String toolVersion,
            GenerationSummary summary) {
        this(jobCode, modelTaskId, modelMode, modelVersion, promptVersion, toolVersion, summary, null);
    }
}
