package com.medkernel.engine.security;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MfaRequirementPolicyTest {

    @Test
    void systemSuperAdminRequiresMfa() {
        assertThat(MfaRequirementPolicy.requiresMfa(List.of("system-superadmin"))).isTrue();
    }
}
