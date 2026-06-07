package com.medkernel.engine.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.audit.AuditRecorder;

class ProjectionSyncServiceTest {

    private final ClinicalGraphProjectionSource source = mock(ClinicalGraphProjectionSource.class);
    private final KnowledgeProjectionSource knowledgeSource = mock(KnowledgeProjectionSource.class);
    private final ProjectionSnapshotRepository snapshots = mock(ProjectionSnapshotRepository.class);
    private final ProjectionSyncRepository syncs = mock(ProjectionSyncRepository.class);
    private final ProjectionRuntimePolicy policy = mock(ProjectionRuntimePolicy.class);
    private final ProjectionExecutionPort executor = mock(ProjectionExecutionPort.class);
    private final AuditRecorder auditRecorder = mock(AuditRecorder.class);
    private final ProjectionSyncService service = new ProjectionSyncService(
        source,
        knowledgeSource,
        snapshots,
        syncs,
        policy,
        executor,
        auditRecorder);

    @Test
    void rebuildClearsProjectionAndStoresRelationalFactsWithMatchingHashes() {
        List<ProjectionSnapshot> stored = new ArrayList<>();
        stored.add(ProjectionSnapshot.fromFact("tenant-A", fact("NODE:STALE:old"), now(), "trace-old"));
        wireSnapshotStore(stored);
        when(policy.graphProjectionEnabled()).thenReturn(true);
        when(policy.difyWorkflowEnabled()).thenReturn(false);
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
        when(policy.graphProjectionEnabled()).thenReturn(true);
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

    @Test
    void rebuildKnowledgeGraphAndSearchProjectionStoresRelationalFactsWithMatchingHashes() {
        List<ProjectionSnapshot> graphRows = new ArrayList<>();
        List<ProjectionSnapshot> searchRows = new ArrayList<>();
        wireSnapshotStore(graphRows, ProjectionTargetType.KNOWLEDGE_GRAPH);
        when(policy.graphProjectionEnabled()).thenReturn(true);
        when(policy.searchProjectionEnabled()).thenReturn(true);
        when(policy.difyWorkflowEnabled()).thenReturn(false);
        when(syncs.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(knowledgeSource.graphFactsForTenant("tenant-A")).thenReturn(List.of(
            fact(ProjectionTargetType.KNOWLEDGE_GRAPH, "NODE:KNOWLEDGE_VERSION:10"),
            fact(ProjectionTargetType.KNOWLEDGE_GRAPH, "EDGE:KNOWLEDGE_IDENTITY:1:HAS_ACTIVE_VERSION:KNOWLEDGE_VERSION:10")));
        when(knowledgeSource.searchFactsForTenant("tenant-A")).thenReturn(List.of(
            fact(ProjectionTargetType.KNOWLEDGE_SEARCH, "NODE:KNOWLEDGE_SEARCH_DOCUMENT:10")));

        ProjectionRebuildResponse graph = service.rebuildKnowledgeGraph("tenant-A", "tester", "trace-1");
        wireSnapshotStore(searchRows, ProjectionTargetType.KNOWLEDGE_SEARCH);
        ProjectionRebuildResponse search = service.rebuildKnowledgeSearch("tenant-A", "tester", "trace-2");

        assertThat(graph.targetType()).isEqualTo(ProjectionTargetType.KNOWLEDGE_GRAPH);
        assertThat(graph.status()).isEqualTo(ProjectionSyncStatus.SUCCESS);
        assertThat(graph.sourceHash()).isEqualTo(graph.projectionHash());
        assertThat(search.targetType()).isEqualTo(ProjectionTargetType.KNOWLEDGE_SEARCH);
        assertThat(search.status()).isEqualTo(ProjectionSyncStatus.SUCCESS);
        assertThat(search.sourceHash()).isEqualTo(search.projectionHash());
        assertThat(graphRows).extracting(ProjectionSnapshot::targetType)
            .containsOnly(ProjectionTargetType.KNOWLEDGE_GRAPH);
        assertThat(searchRows).extracting(ProjectionSnapshot::targetType)
            .containsOnly(ProjectionTargetType.KNOWLEDGE_SEARCH);
    }

    @Test
    void knowledgeGraphConsistencyDetectsStaleSupersededVersionProjection() {
        ProjectionFact current = fact(ProjectionTargetType.KNOWLEDGE_GRAPH, "NODE:KNOWLEDGE_VERSION:10");
        List<ProjectionSnapshot> stored = new ArrayList<>();
        stored.add(ProjectionSnapshot.fromFact("tenant-A", current, now(), "trace-1"));
        stored.add(ProjectionSnapshot.fromFact(
            "tenant-A",
            fact(ProjectionTargetType.KNOWLEDGE_GRAPH, "NODE:KNOWLEDGE_VERSION:5"),
            now(),
            "trace-old"));
        wireSnapshotStore(stored, ProjectionTargetType.KNOWLEDGE_GRAPH);
        when(policy.graphProjectionEnabled()).thenReturn(true);
        when(knowledgeSource.graphFactsForTenant("tenant-A")).thenReturn(List.of(current));

        ProjectionConsistencyReport report = service.checkKnowledgeGraphConsistency("tenant-A");

        assertThat(report.consistent()).isFalse();
        assertThat(report.status()).isEqualTo(ProjectionSyncStatus.FAILED);
        assertThat(report.extra()).extracting(ProjectionDiffItem::factKey)
            .containsExactly("NODE:KNOWLEDGE_VERSION:5");
    }

    @Test
    void listProjectionFactsUsesDatabaseFilteringAndPagination() {
        ProjectionFact patient = fact("NODE:PATIENT:pat-1");
        ProjectionFact observationA = fact("NODE:OBSERVATION:obs-1");
        ProjectionFact observationB = fact("NODE:OBSERVATION:obs-2");
        ProjectionSnapshot observationRowA =
            ProjectionSnapshot.fromFact("tenant-A", observationA, now(), "trace-2");
        ProjectionSnapshot observationRowB =
            ProjectionSnapshot.fromFact("tenant-A", observationB, now(), "trace-3");
        when(snapshots.countByFilter(
            "tenant-A", ProjectionTargetType.CLINICAL_GRAPH, "%observation%"))
            .thenReturn(2L);
        when(snapshots.pageByFilter(
            "tenant-A", ProjectionTargetType.CLINICAL_GRAPH, "%observation%", 0, 1))
            .thenReturn(List.of(observationRowA));
        when(snapshots.pageByFilter(
            "tenant-A", ProjectionTargetType.CLINICAL_GRAPH, "%observation%", 1, 1))
            .thenReturn(List.of(observationRowB));

        PageResponse<ProjectionFactItem> firstPage = service.listProjectionFacts(
            "tenant-A",
            ProjectionTargetType.CLINICAL_GRAPH,
            "observation",
            new PageRequest(1, 1, null));
        PageResponse<ProjectionFactItem> secondPage = service.listProjectionFacts(
            "tenant-A",
            ProjectionTargetType.CLINICAL_GRAPH,
            "observation",
            new PageRequest(2, 1, null));

        assertThat(firstPage.total()).isEqualTo(2);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.items()).extracting(ProjectionFactItem::factKey)
            .containsExactly("NODE:OBSERVATION:obs-1");
        assertThat(secondPage.items()).extracting(ProjectionFactItem::factKey)
            .containsExactly("NODE:OBSERVATION:obs-2");
        verify(snapshots, never())
            .findByTenantIdAndTargetType("tenant-A", ProjectionTargetType.CLINICAL_GRAPH);
    }

    private void wireSnapshotStore(List<ProjectionSnapshot> stored) {
        wireSnapshotStore(stored, ProjectionTargetType.CLINICAL_GRAPH);
    }

    private void wireSnapshotStore(List<ProjectionSnapshot> stored, ProjectionTargetType targetType) {
        doAnswer(invocation -> {
            stored.clear();
            return 1;
        }).when(snapshots).deleteByTenantIdAndTargetType("tenant-A", targetType);
        when(snapshots.saveAll(org.mockito.ArgumentMatchers.anyIterable())).thenAnswer(invocation -> {
            Iterable<ProjectionSnapshot> rows = invocation.getArgument(0);
            List<ProjectionSnapshot> saved = new ArrayList<>();
            rows.forEach(row -> {
                if (row.targetType() == targetType) {
                    stored.add(row);
                    saved.add(row);
                }
            });
            return saved;
        });
        when(snapshots.findByTenantIdAndTargetType("tenant-A", targetType))
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
        return fact(ProjectionTargetType.CLINICAL_GRAPH, factKey);
    }

    private ProjectionFact fact(ProjectionTargetType targetType, String factKey) {
        String[] parts = factKey.split(":");
        if (factKey.startsWith("EDGE:")) {
            return ProjectionFact.edge(targetType, parts[1] + ":" + parts[2], parts[3],
                parts[4] + ":" + parts[5], "payload=" + factKey, now());
        }
        return ProjectionFact.node(targetType, parts[1], parts[2], "payload=" + factKey, now());
    }

    private Instant now() {
        return Instant.parse("2026-06-01T00:00:00Z");
    }
}
