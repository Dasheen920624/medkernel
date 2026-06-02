package com.medkernel.engine.security.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.org.OrgUnitStatus;
import com.medkernel.shared.context.OrgLevel;
import com.medkernel.shared.context.PlatformTenant;

/**
 * 登录前租户字典：客户 / 集团租户优先，平台主租户退居第二层。
 */
class LoginTenantDirectoryServiceTest {

    private OrgUnitRepository orgUnits;
    private LoginTenantDirectoryService service;

    @BeforeEach
    void setUp() {
        orgUnits = Mockito.mock(OrgUnitRepository.class);
        service = new LoginTenantDirectoryService(orgUnits);
    }

    @Test
    void noCustomerTenantShowsPlatformTenantAsPrimaryLayer() {
        when(orgUnits.findAllTenantRoots()).thenReturn(List.of());

        LoginTenantDirectoryResponse response = service.directory();

        assertThat(response.hasCustomerTenants()).isFalse();
        assertThat(response.platformTenant().tenantId()).isEqualTo(PlatformTenant.ID);
        assertThat(response.primaryTenants())
            .extracting(LoginTenantOption::tenantId)
            .containsExactly(PlatformTenant.ID);
    }

    @Test
    void customerTenantsArePrimaryAndPlatformTenantIsSecondLayerOnly() {
        when(orgUnits.findAllTenantRoots()).thenReturn(List.of(
            tenant("t-1", PlatformTenant.DISPLAY_NAME, OrgUnitStatus.ACTIVE),
            tenant("t-hospital", "集团总院", OrgUnitStatus.ACTIVE),
            tenant("t-archived", "已归档医院", OrgUnitStatus.ARCHIVED)
        ));

        LoginTenantDirectoryResponse response = service.directory();

        assertThat(response.hasCustomerTenants()).isTrue();
        assertThat(response.primaryTenants())
            .extracting(LoginTenantOption::tenantId)
            .containsExactly("t-hospital");
        assertThat(response.platformTenant().tenantId()).isEqualTo(PlatformTenant.ID);
    }

    private OrgUnit tenant(String tenantId, String name, OrgUnitStatus status) {
        Instant now = Instant.now();
        return new OrgUnit(
            tenantId, null, tenantId, "/" + tenantId, OrgLevel.TENANT, tenantId, name,
            null, null, status, now, "test", now, "test");
    }
}
