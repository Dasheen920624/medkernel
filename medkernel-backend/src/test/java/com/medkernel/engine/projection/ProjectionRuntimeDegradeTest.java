package com.medkernel.engine.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.clinical.model.ClinicalProjectionStatus;
import com.medkernel.shared.audit.AuditRecorder;

class ProjectionRuntimeDegradeTest {

    private final ClinicalGraphProjectionSource source = mock(ClinicalGraphProjectionSource.class);
    private final ProjectionSnapshotRepository snapshots = mock(ProjectionSnapshotRepository.class);
    private final ProjectionSyncRepository syncs = mock(ProjectionSyncRepository.class);
    private final ProjectionRuntimePolicy policy = mock(ProjectionRuntimePolicy.class);
    private final ProjectionExecutionPort executor = mock(ProjectionExecutionPort.class);
    private final AuditRecorder auditRecorder = mock(AuditRecorder.class);
    private final ProjectionSyncService service = new ProjectionSyncService(
        source,
        snapshots,
        syncs,
        policy,
        executor,
        auditRecorder);

    @Test
    void disabledGraphProjectionReturnsNotSyncedWithoutMutatingSnapshots() {
        when(policy.graphProjectionEnabled()).thenReturn(false);
        when(policy.difyWorkflowEnabled()).thenReturn(false);
        when(syncs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectionRebuildResponse response = service.rebuildClinicalGraph("tenant-A", "tester", "trace-1");

        assertThat(response.status()).isEqualTo(ProjectionSyncStatus.NOT_SYNCED);
        assertThat(response.message()).contains("graph-projection");
        verify(source, never()).factsForTenant(anyString());
        verify(snapshots, never()).deleteByTenantIdAndTargetType(anyString(), any());
        verify(snapshots, never()).saveAll(anyIterable());
    }

    @Test
    void clinicalProjectionStatusFallsBackToNotSyncedWhenGraphDisabled() {
        when(policy.graphProjectionEnabled()).thenReturn(false);
        ProjectionClinicalStatusPort port = new ProjectionClinicalStatusPort(policy, snapshots);

        assertThat(port.status("tenant-A")).isEqualTo(ClinicalProjectionStatus.NOT_SYNCED);
        verify(snapshots, never()).countByTenantIdAndTargetType(anyString(), any());
    }

    @Test
    void clinicalProjectionStatusIsUpOnlyWhenGraphEnabledAndSnapshotExists() {
        when(policy.graphProjectionEnabled()).thenReturn(true);
        when(snapshots.countByTenantIdAndTargetType("tenant-A", ProjectionTargetType.CLINICAL_GRAPH))
            .thenReturn(1L);
        ProjectionClinicalStatusPort port = new ProjectionClinicalStatusPort(policy, snapshots);

        assertThat(port.status("tenant-A")).isEqualTo(ClinicalProjectionStatus.UP);
    }
}
