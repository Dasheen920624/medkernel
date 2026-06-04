package com.medkernel.engine.safety;

import java.time.Instant;

/**
 * OPT-04 临床安全红线静默试运行证据响应。
 */
public record ClinicalRedlineTrialResponse(
    String trialId,
    String redlineId,
    String redlineKey,
    String redlineVersion,
    ClinicalRedlineTrialStatus status,
    Instant observedFrom,
    Instant observedTo,
    long requiredSilentHours,
    long actualSilentHours,
    long evaluatedCaseCount,
    long matchedCaseCount,
    long falsePositiveCaseCount,
    long safetyIncidentCount,
    boolean gatePassed,
    String evidenceReference,
    String traceId
) {}
