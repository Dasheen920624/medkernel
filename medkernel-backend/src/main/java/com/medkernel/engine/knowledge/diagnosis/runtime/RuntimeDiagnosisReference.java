package com.medkernel.engine.knowledge.diagnosis.runtime;

/**
 * 医院运行修订中被锁定的诊断知识版本。
 *
 * <p>临床辅助诊疗只允许使用此引用指向的精确知识版本，不扫描租户下所有 ACTIVE 版本。
 */
public record RuntimeDiagnosisReference(
    String sourceTenantId,
    Long identityId,
    String identityCode,
    String diagnosisName,
    Long knowledgeVersionId,
    String versionNo,
    String authorityLevel
) {
}
