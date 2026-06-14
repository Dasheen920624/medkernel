package com.medkernel.engine.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PermissionCodeTest {

    @Test
    void sandboxRunPermissionIsRegistered() {
        PermissionCode code = PermissionCode.valueOf("SANDBOX_RUN");

        assertThat(code.code()).isEqualTo("sandbox.run");
        assertThat(code.risk()).isEqualTo(PermissionCode.Risk.MEDIUM);
    }
}
