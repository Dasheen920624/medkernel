package com.medkernel.engine.terminology;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class TerminologyAuthoringContractTest {

    @Test
    void terminologyMaintenanceDoesNotCarryReleasePackageOrSecondSignFields() {
        assertNoLegacyFields(TerminologyCandidateGenerationRequest.class);
        assertNoLegacyFields(StandardTermRegistrationRequest.class);
        assertNoLegacyFields(LocalTermRegistrationRequest.class);
        assertNoLegacyFields(TerminologyCandidateConfirmRequest.class);
        assertNoLegacyFields(TerminologyCandidateRejectRequest.class);
        assertNoLegacyFields(TerminologyCandidateBatchConfirmRequest.class);
        assertNoLegacyFields(ResolveConflictRequest.class);
        assertNoLegacyFields(TerminologyCandidateGenerationJob.class);
    }

    private void assertNoLegacyFields(Class<?> type) {
        assertThat(Arrays.stream(type.getRecordComponents()).map(component -> component.getName()))
            .doesNotContain(
                "packageVersion", "packageId", "packageCode",
                "highRiskAcknowledged", "highRiskReason");
    }
}
