package com.medkernel.engine.security.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.medkernel.shared.context.PlatformTenant;

/**
 * 登录租户缺省值必须指向唯一平台主租户。
 */
class LoginRequestTest {

    @Test
    void blankTenantFallsBackToPlatformTenant() {
        assertThat(new LoginRequest("u", "p", null).tenantOrDefault()).isEqualTo(PlatformTenant.ID);
        assertThat(new LoginRequest("u", "p", " ").tenantOrDefault()).isEqualTo(PlatformTenant.ID);
    }

    @Test
    void explicitTenantIsTrimmed() {
        assertThat(new LoginRequest("u", "p", " t-hospital ").tenantOrDefault())
            .isEqualTo("t-hospital");
    }
}
