package com.medkernel.compliance.personnel;

import java.util.List;

import com.medkernel.compliance.identitybinding.IdentityBindingResponse;

/**
 * 人员、任职、账号和身份来源聚合详情。
 */
public record PersonnelDetail(
    Person person,
    AppointmentView primaryAppointment,
    List<AppointmentView> appointments,
    AccountView account,
    List<IdentityBindingResponse> identities,
    OneTimeActivation oneTimeActivation
) {
    public record AppointmentView(
        String appointmentId,
        String organizationId,
        String organizationName,
        String departmentId,
        String departmentName,
        String wardId,
        String wardName,
        AppointmentType appointmentType,
        String positionTitle,
        boolean primary,
        AppointmentStatus status
    ) {
    }

    public record AccountView(
        String userId,
        String username,
        String state
    ) {
    }

    public record OneTimeActivation(
        String username,
        String temporaryPassword
    ) {
    }
}
