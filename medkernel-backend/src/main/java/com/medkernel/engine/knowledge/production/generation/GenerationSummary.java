package com.medkernel.engine.knowledge.production.generation;

import java.util.List;

/** 候选生成汇总（AIK-STD-04）：已生成候选 + 诚实跳过项。 */
public record GenerationSummary(
    List<GeneratedCandidate> candidates,
    List<SkippedType> skipped
) {
    public GenerationSummary {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        skipped = skipped == null ? List.of() : List.copyOf(skipped);
    }
}
