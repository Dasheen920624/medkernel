package com.medkernel.compliance.personnel;

import com.medkernel.compliance.identitybinding.IdentityProviderType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 新增人员及其首个任职、账号和身份来源。
 */
public record PersonCreateRequest(
    @NotBlank String employeeNo,
    @NotBlank String displayName,
    @Valid @NotNull AppointmentInput appointment,
    @Valid AccountInput account,
    @Valid IdentityInput identity
) {
    public record AppointmentInput(
        @NotBlank String organizationId,
        String departmentId,
        String wardId,
        @NotNull AppointmentType appointmentType,
        @Size(max = 128) String positionTitle,
        boolean primary
    ) {
    }

    public record AccountInput(
        @NotBlank String loginName,
        String roleCode
    ) {
    }

    public record IdentityInput(
        @NotNull IdentityProviderType providerType,
        @NotBlank @Size(max = 512) String externalSubject
    ) {
    }
}
