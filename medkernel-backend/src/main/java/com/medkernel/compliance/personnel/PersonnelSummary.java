package com.medkernel.compliance.personnel;

/**
 * 人员与账号列表摘要。
 */
public record PersonnelSummary(
    String personId,
    String employeeNo,
    String displayName,
    PersonStatus status,
    AppointmentType appointmentType,
    String organizationId,
    String organizationName,
    String departmentId,
    String departmentName,
    String wardId,
    String wardName,
    String positionTitle,
    String userId,
    String username,
    String accountState,
    int identityCount
) {
}
