package com.medkernel.engine.context;

import java.time.Instant;
import java.util.List;

import com.medkernel.engine.context.canonical.CanonicalPatient;

final class ClinicalEventTestContexts {

    private ClinicalEventTestContexts() {
    }

    static ContextSnapshotResources resources(
            String patientId, String sourceSystem, String packageVersion, Instant eventTime) {
        return new ContextSnapshotResources(
            new CanonicalPatient(
                patientId,
                "脱敏患者",
                null,
                null,
                List.of(),
                sourceSystem,
                patientId,
                packageVersion,
                eventTime,
                eventTime,
                QualityStatus.VALID),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
