package com.medkernel.engine.knowledge.production.generation;

import java.util.List;

/**
 * 候选生成汇总（AIK-STD-04 + AIK-STD-05）。
 *
 * <p>一次来源生成的结果聚合：已生成并提交的候选清单 + 因无源等诚实跳过的资产类型清单 +
 * 被安全门禁拦截未提审的候选清单（铁律 #1 不伪造、不静默放行）。
 */
public record GenerationSummary(
    List<GeneratedCandidate> candidates,
    List<SkippedType> skipped,
    List<BlockedCandidate> blocked
) {
    public GenerationSummary {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        skipped = skipped == null ? List.of() : List.copyOf(skipped);
        blocked = blocked == null ? List.of() : List.copyOf(blocked);
    }
}
