package com.medkernel.engine.pathway;

/**
 * 机构生效版本锁定的精确路径版本。
 */
public record RuntimePathwayReference(
    String sourceTenantId,
    String templateId,
    String templateCode,
    String pathwayVersionId,
    int versionNo,
    String name,
    String diseaseCode
) {
}
