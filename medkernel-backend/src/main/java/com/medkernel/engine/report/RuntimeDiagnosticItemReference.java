package com.medkernel.engine.report;

/**
 * 机构生效版本中锁定的医技项目说明书版本。
 */
public record RuntimeDiagnosticItemReference(
    String sourceTenantId,
    Long identityId,
    String itemCode,
    String itemName,
    Long knowledgeVersionId,
    String versionNo,
    String authorityLevel,
    String contentHash
) {}
