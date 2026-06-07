package com.medkernel.engine.security.auth;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.org.OrgHierarchyRepository;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.org.OrgUnitStatus;
import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.engine.security.TenantUserRepository;
import com.medkernel.engine.security.UserRoleAssignmentRepository;
import com.medkernel.engine.security.bootstrap.MfaPolicyService;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.audit.IsolatedAuditPublisher;
import com.medkernel.shared.context.OrgLevel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantProvisioningServiceTest {

    @Test
    void listTenantsReturnsOnlyCustomerTenants() {
        OrgUnitRepository orgUnits = mock(OrgUnitRepository.class);
        Instant now = Instant.parse("2026-06-06T00:00:00Z");
        when(orgUnits.findAllTenantRoots()).thenReturn(List.of(
            tenantRoot("t-1", "平台主租户", now),
            tenantRoot("t-renmin", "人民医院", now)
        ));

        TenantProvisioningService service = new TenantProvisioningService(
            orgUnits,
            mock(OrgHierarchyRepository.class),
            mock(PlatformCredentialRepository.class),
            mock(TenantUserRepository.class),
            mock(UserRoleAssignmentRepository.class),
            mock(CredentialPasswordService.class),
            mock(AuditRecorder.class),
            mock(IsolatedAuditPublisher.class),
            mock(MfaPolicyService.class),
            mock(PasswordPolicyService.class)
        );

        assertThat(service.listTenants())
            .extracting(TenantSummary::tenantId)
            .containsExactly("t-renmin");
    }

    private static OrgUnit tenantRoot(String tenantId, String name, Instant now) {
        return new OrgUnit(
            "org-" + tenantId,
            null,
            tenantId,
            "/" + tenantId,
            OrgLevel.TENANT,
            tenantId,
            name,
            null,
            null,
            OrgUnitStatus.ACTIVE,
            now,
            "test",
            now,
            "test"
        );
    }
}
