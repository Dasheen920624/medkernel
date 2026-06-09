package com.medkernel.engine.versioning;

/**
 * 灰度观测处理结果。
 */
public record VersionRolloutObservationResult(
    VersionReleasePlan plan,
    VersionRolloutObservation observation,
    boolean paused,
    boolean readyForFullRelease,
    int currentStagePercent
) {
}
