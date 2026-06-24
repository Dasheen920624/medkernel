package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class ContextSnapshotContractTest {

    @Test
    void clinicalRuntimeContractsExposeOnlyRuntimeReleaseIdentity() {
        assertLiveRequestHasNoPackageSelection(ContextSnapshotRequest.class);
        assertLiveRequestHasNoPackageSelection(ClinicalEventRequest.class);
        assertRuntimeReleaseContract(ContextSnapshot.class);
        assertRuntimeReleaseContract(ContextSnapshotResponse.class);
        assertRuntimeReleaseContract(ClinicalEvent.class);
        assertRuntimeReleaseContract(ClinicalEventDetailResponse.class);
        assertRuntimeReleaseContract(ClinicalEventContext.class);
    }

    @Test
    void liveRequestDoesNotForceClinicalClientsToTrackPackageReleases() {
        assertLiveRequestHasNoPackageSelection(ContextSnapshotRequest.class);
        assertLiveRequestHasNoPackageSelection(ClinicalEventRequest.class);
    }

    private void assertRuntimeReleaseContract(Class<?> recordType) {
        assertThat(Arrays.stream(recordType.getRecordComponents()).map(component -> component.getName()))
            .contains("runtimeReleaseId")
            .doesNotContain(
                "packageId",
                "packageCode",
                "packageVersion",
                "knowledgePackageVersion",
                "rulePackageVersion",
                "pathwayPackageVersion");
    }

    private void assertLiveRequestHasNoPackageSelection(Class<?> recordType) {
        assertThat(Arrays.stream(recordType.getRecordComponents()).map(component -> component.getName()))
            .doesNotContain("packageId", "packageCode", "packageVersion", "knowledgePackageVersion");
    }
}
