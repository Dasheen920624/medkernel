package com.medkernel.engine.pathway;

import java.util.List;

/**
 * 患者路径入径候选响应。
 */
public record PathwayEntryCandidateResponse(
    String contextSnapshotId,
    String triggerPoint,
    List<PathwayEntryCandidate> candidates
) {
    public PathwayEntryCandidateResponse {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}
