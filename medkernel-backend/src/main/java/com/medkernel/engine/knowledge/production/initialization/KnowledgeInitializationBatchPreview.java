package com.medkernel.engine.knowledge.production.initialization;

/** 初始化发行批次预览与稳定摘要。 */
public record KnowledgeInitializationBatchPreview(
    InitializationManifestValidation hashes,
    int sourceCount,
    int candidateCount,
    int lowCount,
    int mediumCount,
    int highCount
) {
}
