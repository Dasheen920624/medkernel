package com.medkernel.engine.context;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.medkernel.engine.context.canonical.CanonicalEncounter;
import com.medkernel.engine.context.canonical.CanonicalPatient;

/**
 * ContextSnapshotService 测试公共 fixture，跨多个测试类复用避免漂移。
 */
public final class ContextSnapshotServiceFixtures {

    private ContextSnapshotServiceFixtures() {}

    public static ContextSnapshotRequest sampleRequest() {
        return new ContextSnapshotRequest(
            null, null, null, null, null, null, null, null, null, null, List.of(),
            "MPI-1", "ENC-1", "ORG-1", "pkg-1", validResources());
    }

    public static ContextSnapshotResources validResources() {
        var patient = new CanonicalPatient("MPI-1", "张三",
            LocalDate.of(1980, 1, 1), "M",
            List.of(), "HIS", "rec-1", "v1",
            Instant.now(), Instant.now(), QualityStatus.VALID);
        var enc = new CanonicalEncounter("ENC-1", "IP", Instant.now(), null,
            "DEPT-A", "DOC-A", null, "HIS", "rec-2", "v1",
            Instant.now(), Instant.now(), QualityStatus.VALID);
        return new ContextSnapshotResources(patient,
            List.of(), List.of(enc), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
