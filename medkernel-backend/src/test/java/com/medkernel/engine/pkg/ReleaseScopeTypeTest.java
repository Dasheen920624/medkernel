package com.medkernel.engine.pkg;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class ReleaseScopeTypeTest {

    @Test
    void containsOnlyOrganizationScopeValues() {
        assertThat(Arrays.stream(ReleaseScopeType.values()).map(Enum::name))
            .containsExactly("ALL", "REGION", "FACILITY", "CAMPUS", "DEPARTMENT", "WARD");
    }
}
