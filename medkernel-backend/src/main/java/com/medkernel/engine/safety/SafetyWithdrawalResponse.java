package com.medkernel.engine.safety;

/**
 * 安全撤回执行结果。
 */
public record SafetyWithdrawalResponse(
    Long withdrawalId,
    Long identityId,
    Long versionId,
    String versionStatus,
    SafetyImpactResponse impact,
    String traceId
) {}
