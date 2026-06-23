package com.medkernel.compliance.personnel;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import com.medkernel.engine.integration.masterdata.MasterDataPersonCommand;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

class ComplianceMasterDataPersonnelAdapterTest {

    private final PersonnelService personnel = mock(PersonnelService.class);
    private final Authentication authentication = mock(Authentication.class);
    private final ComplianceMasterDataPersonnelAdapter adapter =
        new ComplianceMasterDataPersonnelAdapter(personnel);

    @Test
    void convertsNeutralMasterDataCommandToComplianceEnums() {
        MasterDataPersonCommand command = new MasterDataPersonCommand(
            "EMP-001", "王医生", "HOSP-A", "CARDIO", "CARDIO-W1",
            "INTERNAL", "主治医师", "EMP-001", "clinical-user",
            "EMPLOYEE_NO", "EMP-001", "ACTIVE");
        when(personnel.syncFromExternal(any(), any())).thenReturn("person-internal");

        adapter.upsert(command, authentication);

        verify(personnel).syncFromExternal(
            org.mockito.ArgumentMatchers.argThat(mapped ->
                mapped.appointmentType() == AppointmentType.INTERNAL
                    && mapped.identityProvider()
                        == com.medkernel.compliance.identitybinding.IdentityProviderType.EMPLOYEE_NO
                    && mapped.status() == PersonStatus.ACTIVE
                    && "CARDIO-W1".equals(mapped.wardCode())),
            org.mockito.ArgumentMatchers.same(authentication));
    }

    @Test
    void rejectsUnknownComplianceEnumInsteadOfLeakingImplementationError() {
        MasterDataPersonCommand command = new MasterDataPersonCommand(
            "EMP-001", "王医生", "HOSP-A", "CARDIO", null,
            "UNKNOWN", null, null, null, null, null, "ACTIVE");

        assertThatThrownBy(() -> adapter.upsert(command, authentication))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST)
            .hasMessageContaining("任职类型");
    }
}
