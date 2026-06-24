package com.medkernel.compliance.exportconfirmation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.medkernel.compliance.evidence.dto.EvidenceCreateDto;
import com.medkernel.compliance.evidence.dto.EvidenceResponse;
import com.medkernel.compliance.evidence.service.EvidenceService;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecordCommand;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.export.ExportArtifact;
import com.medkernel.shared.export.ExportArtifactProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExportConfirmationServiceTest {

    private static final String DIGEST =
        "sm3:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private ExportConfirmationRepository repository;
    private EvidenceService evidenceService;
    private AuditRecorder auditRecorder;
    private ExportArtifactProvider artifactProvider;
    private ExportConfirmationService service;

    @BeforeEach
    void setUp() {
        repository = mock(ExportConfirmationRepository.class);
        evidenceService = mock(EvidenceService.class);
        auditRecorder = mock(AuditRecorder.class);
        artifactProvider = mock(ExportArtifactProvider.class);
        service = new ExportConfirmationService(
            repository,
            evidenceService,
            auditRecorder,
            List.of(artifactProvider),
            new ObjectMapper()
        );
    }

    @Test
    void confirmExportFreezesScopeAndCreatesEvidenceForCurrentActor() {
        when(repository.findByTenantIdAndIdempotencyKey("t-1", "idem-001"))
            .thenReturn(Optional.empty());
        when(evidenceService.createSnapshot(eq("t-1"), any(EvidenceCreateDto.class)))
            .thenReturn(evidence(
                "evd-exp-audit-event-idem-001-confirmation",
                "/api/v1/compliance/evidence/snapshots/confirmation/file"
            ));
        when(repository.save(any(ExportConfirmation.class)))
            .thenAnswer(invocation -> invocation.<ExportConfirmation>getArgument(0).withId(11L));

        ExportConfirmationResponse response = service.confirmExport(
            "t-1",
            new ExportConfirmationRequest(
                "AUDIT_EVENT",
                Map.of(
                    "resourceType", "AUDIT_EVENT",
                    "filters", Map.of("action", "EXPORT"),
                    "selectedScope", "FILTERED_RESULT"
                ),
                "复核当前导出操作",
                "idem-001"
            ),
            "auditor-1"
        );

        assertThat(response.confirmationId()).isEqualTo("exp-audit-event-idem-001");
        assertThat(response.status()).isEqualTo(ExportConfirmationStatus.CONFIRMED);
        assertThat(response.confirmedBy()).isEqualTo("auditor-1");
        assertThat(response.confirmationEvidenceId())
            .isEqualTo("evd-exp-audit-event-idem-001-confirmation");

        ArgumentCaptor<EvidenceCreateDto> evidence = ArgumentCaptor.forClass(EvidenceCreateDto.class);
        verify(evidenceService).createSnapshot(eq("t-1"), evidence.capture());
        assertThat(evidence.getValue().evidenceType()).isEqualTo("COMPLIANCE_EXPORT_CONFIRMATION");
        assertThat(evidence.getValue().action()).isEqualTo("CONFIRM");
        assertThat(evidence.getValue().subjectType()).isEqualTo("mk_compliance_export_confirmation");
        assertThat(evidence.getValue().payloadSnapshot())
            .contains("\"confirmationId\":\"exp-audit-event-idem-001\"")
            .contains("\"confirmedBy\":\"auditor-1\"");

        ArgumentCaptor<AuditRecordCommand> audit = ArgumentCaptor.forClass(AuditRecordCommand.class);
        verify(auditRecorder).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.CREATE);
        assertThat(audit.getValue().targetType()).isEqualTo("mk_compliance_export_confirmation");
    }

    @Test
    void confirmExportReusesIdempotentConfirmationWithoutCreatingNewEvidence() {
        ExportConfirmation existing = confirmedAuditExport();
        when(repository.findByTenantIdAndIdempotencyKey("t-1", "idem-001"))
            .thenReturn(Optional.of(existing));

        ExportConfirmationResponse response = service.confirmExport(
            "t-1",
            new ExportConfirmationRequest(
                "AUDIT_EVENT",
                Map.of("resourceType", "AUDIT_EVENT"),
                "复核当前导出操作",
                "idem-001"
            ),
            "auditor-1"
        );

        assertThat(response.confirmationId()).isEqualTo(existing.confirmationId());
        verify(repository, never()).save(any());
        verify(evidenceService, never()).createSnapshot(any(), any());
    }

    @Test
    void completeExportFromJobRequiresMatchingArtifactAndCreatesExportEvidence() {
        ExportConfirmation existing = confirmedAuditExport();
        when(repository.findByTenantIdAndConfirmationId("t-1", existing.confirmationId()))
            .thenReturn(Optional.of(existing));
        when(artifactProvider.supports("audit_event")).thenReturn(true);
        when(artifactProvider.completedExportArtifact("job-audit-1"))
            .thenReturn(new ExportArtifact(
                "job-audit-1",
                "AUDIT_EVENT",
                existing.exportScopeSnapshot(),
                existing.idempotencyKey(),
                "/medkernel/api/v1/large-lists/exports/job-audit-1/download",
                DIGEST
            ));
        when(evidenceService.createSnapshot(eq("t-1"), any(EvidenceCreateDto.class)))
            .thenReturn(evidence(
                "evd-exp-audit-event-idem-001-export",
                "/api/v1/compliance/evidence/snapshots/export/file"
            ));
        when(repository.save(any(ExportConfirmation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ExportConfirmationResponse response = service.completeExportFromJob(
            "t-1",
            existing.confirmationId(),
            new ExportJobCompletionRequest("job-audit-1", "导出文件已生成", 1L),
            "auditor-1"
        );

        assertThat(response.status()).isEqualTo(ExportConfirmationStatus.EXPORTED);
        assertThat(response.exportDigest()).isEqualTo(DIGEST);
        assertThat(response.version()).isEqualTo(2L);
        ArgumentCaptor<EvidenceCreateDto> evidence = ArgumentCaptor.forClass(EvidenceCreateDto.class);
        verify(evidenceService).createSnapshot(eq("t-1"), evidence.capture());
        assertThat(evidence.getValue().payloadSnapshot())
            .contains("\"confirmationId\":\"exp-audit-event-idem-001\"")
            .contains("\"jobId\":\"job-audit-1\"");
    }

    @Test
    void completeExportFromJobByIdempotencyKeyFindsTheFrozenConfirmation() {
        ExportConfirmation existing = confirmedAuditExport();
        when(repository.findByTenantIdAndIdempotencyKey("t-1", "idem-001"))
            .thenReturn(Optional.of(existing));
        when(artifactProvider.supports("audit_event")).thenReturn(true);
        when(artifactProvider.completedExportArtifact("job-audit-1"))
            .thenReturn(new ExportArtifact(
                "job-audit-1",
                "AUDIT_EVENT",
                existing.exportScopeSnapshot(),
                existing.idempotencyKey(),
                "/medkernel/api/v1/large-lists/exports/job-audit-1/download",
                DIGEST
            ));
        when(evidenceService.createSnapshot(eq("t-1"), any(EvidenceCreateDto.class)))
            .thenReturn(evidence(
                "evd-exp-audit-event-idem-001-export",
                "/api/v1/compliance/evidence/snapshots/export/file"
            ));
        when(repository.save(any(ExportConfirmation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ExportConfirmationResponse response = service.completeExportFromJobByIdempotencyKey(
            "t-1",
            "idem-001",
            "job-audit-1",
            "后台异步导出任务已生成真实文件",
            "auditor-1"
        );

        assertThat(response.status()).isEqualTo(ExportConfirmationStatus.EXPORTED);
        verify(repository).findByTenantIdAndIdempotencyKey("t-1", "idem-001");
    }

    @Test
    void completeExportFromJobRejectsArtifactOutsideConfirmedScope() {
        ExportConfirmation existing = confirmedAuditExport();
        when(repository.findByTenantIdAndConfirmationId("t-1", existing.confirmationId()))
            .thenReturn(Optional.of(existing));
        when(artifactProvider.supports("audit_event")).thenReturn(true);
        when(artifactProvider.completedExportArtifact("job-audit-2"))
            .thenReturn(new ExportArtifact(
                "job-audit-2",
                "AUDIT_EVENT",
                "{\"resourceType\":\"AUDIT_EVENT\",\"filters\":{\"action\":\"LOGIN\"},"
                    + "\"selectedScope\":\"FILTERED_RESULT\"}",
                existing.idempotencyKey(),
                "/medkernel/api/v1/large-lists/exports/job-audit-2/download",
                DIGEST
            ));

        ApiException exception = catchThrowableOfType(
            () -> service.completeExportFromJob(
                "t-1",
                existing.confirmationId(),
                new ExportJobCompletionRequest("job-audit-2", "登记导出", 1L),
                "auditor-1"
            ),
            ApiException.class
        );

        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(exception.getMessage()).contains("确认记录");
        verify(repository, never()).save(any());
    }

    private ExportConfirmation confirmedAuditExport() {
        Instant now = Instant.parse("2026-06-05T00:00:00Z");
        return new ExportConfirmation(
            1L,
            "exp-audit-event-idem-001",
            "t-1",
            "audit_event",
            "{\"resourceType\":\"AUDIT_EVENT\",\"filters\":{\"action\":\"EXPORT\"},"
                + "\"selectedScope\":\"FILTERED_RESULT\"}",
            "idem-001",
            "复核当前导出操作",
            "auditor-1",
            now,
            "CONFIRMED",
            null,
            null,
            "evd-exp-audit-event-idem-001-confirmation",
            "/api/v1/compliance/evidence/snapshots/confirmation/file",
            null,
            null,
            1L,
            now,
            "auditor-1",
            now,
            "auditor-1",
            "trace-test"
        );
    }

    private EvidenceResponse evidence(String evidenceId, String fileUri) {
        Instant now = Instant.parse("2026-06-05T00:00:00Z");
        return new EvidenceResponse(
            9L,
            evidenceId,
            "t-1",
            "trace-test",
            "COMPLIANCE_EXPORT",
            "EXPORT",
            "mk_compliance_export_confirmation",
            "exp-audit-event-idem-001",
            "合规导出确认证据",
            "{\"confirmationId\":\"exp-audit-event-idem-001\"}",
            "sm3:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            fileUri,
            "sm3:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
            "SM3_WITH_SM2",
            "sig",
            "pub",
            true,
            now,
            "system"
        );
    }
}
