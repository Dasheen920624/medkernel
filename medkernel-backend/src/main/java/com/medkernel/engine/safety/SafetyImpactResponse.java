package com.medkernel.engine.safety;

import java.util.List;

/**
 * 安全撤回影响集合摘要。
 */
public record SafetyImpactResponse(
    Long withdrawalId,
    Long identityId,
    Long versionId,
    int patientCaseCount,
    int patientPathwayCount,
    int syncTargetCount,
    int taskCount,
    String impactDigest,
    List<SafetyAffectedTaskResponse> tasks,
    String traceId
) {}
