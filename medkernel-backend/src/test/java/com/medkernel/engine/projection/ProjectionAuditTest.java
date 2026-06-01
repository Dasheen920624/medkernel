package com.medkernel.engine.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecordCommand;
import com.medkernel.shared.audit.AuditRecorder;

class ProjectionAuditTest {

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
    void rebuildRecordsRealAuditWithSyncHashesAndStatus() {
        List<ProjectionSnapshot> stored = new ArrayList<>();
        when(policy.graphProjectionEnabled()).thenReturn(true);
        when(policy.difyWorkflowEnabled()).thenReturn(false);
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

        ProjectionRebuildResponse response = service.rebuildClinicalGraph("tenant-A", "tester", "trace-1");

        assertThat(response.status()).isEqualTo(ProjectionSyncStatus.SUCCESS);
        verify(auditRecorder).record(argThat(command ->
            command.action() == AuditAction.EXECUTE
                && command.targetType().equals("mk_projection_sync")
                && command.targetId().equals(response.syncId())
                && String.valueOf(command.after()).contains(response.sourceHash())
                && String.valueOf(command.after()).contains("SUCCESS")));
    }

    @Test
    void disabledProjectionRecordsNotSyncedAudit() {
        when(policy.graphProjectionEnabled()).thenReturn(false);
        when(syncs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectionRebuildResponse response = service.rebuildClinicalGraph("tenant-A", "tester", "trace-1");

        assertThat(response.status()).isEqualTo(ProjectionSyncStatus.NOT_SYNCED);
        verify(auditRecorder).record(argThat(this::isNotSyncedAudit));
    }

    private boolean isNotSyncedAudit(AuditRecordCommand command) {
        return command.action() == AuditAction.EXECUTE
            && command.targetType().equals("mk_projection_sync")
            && String.valueOf(command.after()).contains("NOT_SYNCED")
            && String.valueOf(command.after()).contains("tenant-A");
    }
}
