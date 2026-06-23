package com.medkernel.engine.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PermissionCodeTest {

    @Test
    void sandboxRunAndGovernancePermissionsAreRegisteredSeparately() {
        PermissionCode run = PermissionCode.valueOf("SANDBOX_RUN");
        PermissionCode manage = PermissionCode.valueOf("SANDBOX_MANAGE");

        assertThat(run.code()).isEqualTo("sandbox.run");
        assertThat(run.risk()).isEqualTo(PermissionCode.Risk.MEDIUM);
        assertThat(manage.code()).isEqualTo("sandbox.manage");
        assertThat(manage.risk()).isEqualTo(PermissionCode.Risk.MEDIUM);
    }
}
