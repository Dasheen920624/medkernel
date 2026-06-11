package com.medkernel.shared.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.medkernel.engine.versioning.PlatformAuthority;
import org.junit.jupiter.api.Test;

/**
 * 平台主租户边界：唯一内置，承载全局知识源，不等同客户集团或医院租户。
 */
class PlatformTenantTest {

    @Test
    void platformTenantIsSingleBuiltInKnowledgeSourceTenant() {
        assertThat(PlatformTenant.ID).isEqualTo("t-1");
        assertThat(PlatformTenant.DISPLAY_NAME).isEqualTo("平台治理空间（唯一内置）");
        assertThat(PlatformTenant.isPlatformTenant("t-1")).isTrue();
        assertThat(PlatformTenant.isPlatformTenant("t-hospital")).isFalse();
    }

    @Test
    void versioningUsesTheSamePlatformTenantInsteadOfASecondTechnicalTenant() {
        assertThat(PlatformAuthority.PLATFORM_TENANT_ID).isEqualTo(PlatformTenant.ID);
    }
}
