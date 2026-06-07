package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class ContextSnapshotContractTest {

    @Test
    void requestEntityAndResponseExposeOneUnifiedPackageVersion() {
        assertUnifiedPackageContract(ContextSnapshotRequest.class);
        assertUnifiedPackageContract(ContextSnapshot.class);
        assertUnifiedPackageContract(ContextSnapshotResponse.class);
    }

    private void assertUnifiedPackageContract(Class<?> recordType) {
        assertThat(Arrays.stream(recordType.getRecordComponents()).map(component -> component.getName()))
            .contains("packageVersion")
            .doesNotContain("knowledgePackageVersion", "rulePackageVersion", "pathwayPackageVersion");
    }
}
