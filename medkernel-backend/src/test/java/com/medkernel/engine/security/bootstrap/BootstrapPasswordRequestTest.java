package com.medkernel.engine.security.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.medkernel.shared.context.PlatformTenant;

/**
 * 首次部署管理员必须默认归属唯一平台主租户。
 */
class BootstrapPasswordRequestTest {

    @Test
    void blankTenantFallsBackToPlatformTenant() {
        assertThat(new BootstrapPasswordRequest("token", null, "owner", "pw").tenantOrDefault())
            .isEqualTo(PlatformTenant.ID);
        assertThat(new BootstrapPasswordRequest("token", " ", "owner", "pw").tenantOrDefault())
            .isEqualTo(PlatformTenant.ID);
    }
}
