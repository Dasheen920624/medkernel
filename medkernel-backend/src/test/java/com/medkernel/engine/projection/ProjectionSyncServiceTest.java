package com.medkernel.engine.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ProjectionSyncServiceTest {

    private final ClinicalGraphProjectionSource source = mock(ClinicalGraphProjectionSource.class);
    private final ProjectionSnapshotRepository snapshots = mock(ProjectionSnapshotRepository.class);
    private final ProjectionSyncRepository syncs = mock(ProjectionSyncRepository.class);
    private final ProjectionSyncService service = new ProjectionSyncService(source, snapshots, syncs);

    @Test
    void rebuildClearsProjectionAndStoresRelationalFactsWithMatchingHashes() {
        List<ProjectionSnapshot> stored = new ArrayList<>();
        stored.add(ProjectionSnapshot.fromFact("tenant-A", fact("NODE:STALE:old"), now(), "trace-old"));
        wireSnapshotStore(stored);
        when(syncs.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(source.factsForTenant("tenant-A")).thenReturn(List.of(
            fact("NODE:PATIENT:pat-1"),
            fact("NODE:OBSERVATION:obs-1")));

        ProjectionRebuildResponse response = service.rebuildClinicalGraph("tenant-A", "tester", "trace-1");

        assertThat(response.status()).isEqualTo(ProjectionSyncStatus.SUCCESS);
        assertThat(response.sourceCount()).isEqualTo(2);
        assertThat(response.projectionCount()).isEqualTo(2);
        assertThat(response.sourceHash()).isEqualTo(response.projectionHash());
        assertThat(stored).extracting(ProjectionSnapshot::factKey)
            .containsExactly("NODE:PATIENT:pat-1", "NODE:OBSERVATION:obs-1");
    }

    @Test
    void consistencyReportDetectsMissingExtraAndChangedProjectionFacts() {
        ProjectionFact patient = fact("NODE:PATIENT:pat-1");
        ProjectionFact observation = fact("NODE:OBSERVATION:obs-1");
        List<ProjectionSnapshot> stored = new ArrayList<>();
        stored.add(snapshotWithHash(patient, "0".repeat(64)));
        stored.add(ProjectionSnapshot.fromFact("tenant-A", fact("NODE:CLAIM:claim-9"), now(), "trace-1"));
        wireSnapshotStore(stored);
        when(source.factsForTenant("tenant-A")).thenReturn(List.of(patient, observation));

        ProjectionConsistencyReport report = service.checkClinicalGraphConsistency("tenant-A");

        assertThat(report.consistent()).isFalse();
        assertThat(report.missing()).extracting(ProjectionDiffItem::factKey)
            .containsExactly("NODE:OBSERVATION:obs-1");
        assertThat(report.extra()).extracting(ProjectionDiffItem::factKey)
            .containsExactly("NODE:CLAIM:claim-9");
        assertThat(report.changed()).extracting(ProjectionDiffItem::factKey)
            .containsExactly("NODE:PATIENT:pat-1");
    }

    private void wireSnapshotStore(List<ProjectionSnapshot> stored) {
        doAnswer(invocation -> {
            stored.clear();
            return 1;
        }).when(snapshots).deleteByTenantIdAndTargetType("tenant-A", ProjectionTargetType.CLINICAL_GRAPH);
        when(snapshots.saveAll(org.mockito.ArgumentMatchers.anyIterable())).thenAnswer(invocation -> {
            Iterable<ProjectionSnapshot> rows = invocation.getArgument(0);
            List<ProjectionSnapshot> saved = new ArrayList<>();
            rows.forEach(row -> {
                stored.add(row);
                saved.add(row);
            });
            return saved;
        });
        when(snapshots.findByTenantIdAndTargetType("tenant-A", ProjectionTargetType.CLINICAL_GRAPH))
            .thenAnswer(invocation -> List.copyOf(stored));
    }

    private ProjectionSnapshot snapshotWithHash(ProjectionFact fact, String contentHash) {
        ProjectionSnapshot snapshot = ProjectionSnapshot.fromFact("tenant-A", fact, now(), "trace-1");
        return new ProjectionSnapshot(
            snapshot.id(),
            snapshot.tenantId(),
            snapshot.targetType(),
            snapshot.factKey(),
            snapshot.factKind(),
            snapshot.objectType(),
            snapshot.objectId(),
            snapshot.subjectKey(),
            snapshot.predicate(),
            snapshot.objectKey(),
            contentHash,
            snapshot.canonicalPayload(),
            snapshot.sourceUpdatedAt(),
            snapshot.syncedAt(),
            snapshot.traceId());
    }

    private ProjectionFact fact(String factKey) {
        String[] parts = factKey.split(":");
        return ProjectionFact.node(parts[1], parts[2], "payload=" + factKey, now());
    }

    private Instant now() {
        return Instant.parse("2026-06-01T00:00:00Z");
    }
}
