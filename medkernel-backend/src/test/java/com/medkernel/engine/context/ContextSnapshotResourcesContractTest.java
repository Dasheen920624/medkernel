package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContextSnapshotResourcesContractTest {

    @Test
    void resourcesExposeOnlyTheCompleteCanonicalConstructor() {
        assertThat(ContextSnapshotResources.class.getConstructors()).hasSize(1);
    }
}
