package com.medkernel.compliance.personnel;

/** 人员批量导入任务状态。 */
public enum PersonnelImportStatus {
    VALIDATING,
    HAS_ISSUES,
    READY,
    PROCESSING,
    PARTIAL,
    COMPLETED,
    CANCELLED
}
