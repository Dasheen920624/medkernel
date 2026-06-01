package com.medkernel.engine.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.medkernel.shared.audit.AuditRecorder;

class ProjectionDifyExecutorBoundaryTest {

    @Test
    void difyExecutorCommandCarriesOnlySyncMetadataNeverAuthorityFacts() {
        List<String> componentNames = Arrays.stream(ProjectionExecutionCommand.class.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();

        assertThat(componentNames)
            .containsExactly("tenantId", "syncId", "targetType", "sourceCount", "sourceHash", "traceId");
        assertThat(componentNames)
            .doesNotContain("facts", "canonicalPayload", "patientId", "nameCipher", "identityNoCipher", "phoneCipher");
    }

    @Test
    void difyExecutorReceivesOnlyAggregateHashWhenEnabled() {
        ClinicalGraphProjectionSource source = mock(ClinicalGraphProjectionSource.class);
        ProjectionSnapshotRepository snapshots = mock(ProjectionSnapshotRepository.class);
        ProjectionSyncRepository syncs = mock(ProjectionSyncRepository.class);
        ProjectionRuntimePolicy policy = mock(ProjectionRuntimePolicy.class);
        ProjectionExecutionPort executor = mock(ProjectionExecutionPort.class);
        AuditRecorder auditRecorder = mock(AuditRecorder.class);
        ProjectionSyncService service = new ProjectionSyncService(
            source,
            snapshots,
            syncs,
            policy,
            executor,
            auditRecorder);
        List<ProjectionSnapshot> stored = new ArrayList<>();

        when(policy.graphProjectionEnabled()).thenReturn(true);
        when(policy.difyWorkflowEnabled()).thenReturn(true);
        when(syncs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(source.factsForTenant("tenant-A")).thenReturn(List.of(ProjectionFact.node(
            "PATIENT", "pat-1", "payload=NODE:PATIENT:pat-1", Instant.parse("2026-06-01T00:00:00Z"))));
        when(snapshots.saveAll(any())).thenAnswer(invocation -> {
            Iterable<ProjectionSnapshot> rows = invocation.getArgument(0);
            rows.forEach(stored::add);
            return List.copyOf(stored);
        });
        when(snapshots.findByTenantIdAndTargetType("tenant-A", ProjectionTargetType.CLINICAL_GRAPH))
            .thenAnswer(invocation -> List.copyOf(stored));
        when(executor.execute(any())).thenReturn(ProjectionExecutionResult.notSynced("NOT_SYNCED：未配置真实 Dify 执行器"));

        ProjectionRebuildResponse response = service.rebuildClinicalGraph("tenant-A", "tester", "trace-1");

        verify(executor).execute(org.mockito.ArgumentMatchers.argThat(command ->
            command.tenantId().equals("tenant-A")
                && command.targetType() == ProjectionTargetType.CLINICAL_GRAPH
                && command.sourceCount() == 1
                && command.sourceHash().equals(response.sourceHash())
                && command.traceId().equals("trace-1")));
        assertThat(response.difyExecutionStatus()).isEqualTo(ProjectionSyncStatus.NOT_SYNCED);
    }
}
