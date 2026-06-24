package com.medkernel.engine.pathway;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class PathwayRuntimeContractTest {

    @Test
    void patientPathwayPinsRuntimeReleaseAndExactPathwayVersion() {
        assertThat(componentNames(PatientPathway.class))
            .contains("runtimeReleaseId", "pathwayVersionId");
    }

    @Test
    void clinicalRequestsCarryTriggerPointButNoPackageOrReleaseSelector() {
        assertThat(componentNames(PatientPathwayEnterRequest.class))
            .contains("triggerPoint")
            .doesNotContain("packageId", "packageVersion", "runtimeReleaseId", "pathwayVersionId");
        assertThat(componentNames(PathwayAdvanceRequest.class))
            .contains("triggerPoint")
            .doesNotContain("packageId", "packageVersion", "runtimeReleaseId", "pathwayVersionId");
        assertThat(componentNames(PathwaySimulateRequest.class))
            .doesNotContain("packageId", "packageVersion", "runtimeReleaseId", "pathwayVersionId");
    }

    @Test
    void pathwayAuthoringContractsDoNotCarryLegacyPackageOwnership() {
        assertThat(componentNames(PathwayTemplate.class))
            .doesNotContain("packageId", "packageVersion");
        assertThat(componentNames(PathwayTemplateCreateRequest.class))
            .doesNotContain("packageId", "packageVersion", "templateVersion");
        assertThat(componentNames(PathwayTemplateFilter.class))
            .doesNotContain("packageId", "packageVersion");
        assertThat(componentNames(SpecialtyMetricBinding.class))
            .doesNotContain("packageId", "packageVersion");
        assertThat(componentNames(PathwayOutcomeBinding.class))
            .doesNotContain("packageId", "packageVersion");
        assertThat(componentNames(PathwayOutcomeBindingRequest.class))
            .doesNotContain("packageId", "packageVersion");
    }

    private String[] componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
            .map(RecordComponent::getName)
            .toArray(String[]::new);
    }
}
