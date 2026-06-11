package com.medkernel.compliance.personnel;

import java.util.List;

/**
 * 批量导入预检或提交结果。
 */
public record PersonnelImportResponse(
    String jobId,
    String fileName,
    PersonnelImportStatus status,
    int totalRows,
    int validRows,
    int conflictRows,
    int successRows,
    int failureRows,
    List<RowResult> rows,
    List<PersonnelDetail.OneTimeActivation> oneTimeActivations
) {
    public record RowResult(
        int rowNo,
        String employeeNo,
        String displayName,
        String action,
        String status,
        String message,
        String resultPersonId
    ) {
    }
}
