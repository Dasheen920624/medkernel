package com.medkernel.compliance.personnel;

import com.medkernel.compliance.identitybinding.IdentityProviderType;

/**
 * 院内业务系统人员、任职、用户和身份来源同步命令。
 */
public record PersonnelSyncCommand(
    String employeeNo,
    String displayName,
    String organizationCode,
    String departmentCode,
    String wardCode,
    AppointmentType appointmentType,
    String positionTitle,
    String userId,
    String roleCode,
    IdentityProviderType identityProvider,
    String identitySubject,
    PersonStatus status,
    boolean disable
) {
}
