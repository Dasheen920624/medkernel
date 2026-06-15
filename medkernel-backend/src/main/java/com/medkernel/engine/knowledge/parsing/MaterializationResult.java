package com.medkernel.engine.knowledge.parsing;

/**
 * 物化结果（AIK-STD-02）：物化的 source_version + 章节数 + 新增片段数（诚实计数，幂等跳过不计）。
 */
public record MaterializationResult(
    Long sourceVersionId,
    int sectionCount,
    int fragmentCount
) {
}
