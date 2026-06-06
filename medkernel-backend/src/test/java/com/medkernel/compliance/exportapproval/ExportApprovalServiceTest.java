package com.medkernel.compliance.exportapproval;

import java.time.Instant;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExportApprovalServiceTest {

    private static final String DIGEST = "sm3:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private ExportApprovalRepository repository;
    private EvidenceService evidenceService;
    private AuditRecorder auditRecorder;
    private ExportApprovalService service;

    @BeforeEach
    void setUp() {
        repository = mock(ExportApprovalRepository.class);
        evidenceService = mock(EvidenceService.class);
        auditRecorder = mock(AuditRecorder.class);
        service = new ExportApprovalService(repository, evidenceService, auditRecorder, new ObjectMapper());
    }

    @Test
    void requestExportCreatesRequestedApprovalAndAudit() {
        when(repository.findByTenantIdAndIdempotencyKey("t-1", "idem-001")).thenReturn(Optional.empty());
        when(repository.save(any(ExportApproval.class)))
            .thenAnswer(invocation -> invocation.<ExportApproval>getArgument(0).withId(11L));
        ExportApprovalRequest request = new ExportApprovalRequest(
            "Clinical Case",
            Map.of("patientId", "p-1", "reasonCode", "audit-review"),
            "合规审计需要导出当前患者证据包",
            "idem-001");

        ExportApprovalResponse response = service.requestExport("t-1", request, "doctor-1");

        assertThat(response.approvalId()).isEqualTo("exp-clinical-case-idem-001");
        assertThat(response.status()).isEqualTo(ExportApprovalStatus.REQUESTED);
        ArgumentCaptor<ExportApproval> saved = ArgumentCaptor.forClass(ExportApproval.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().resourceType()).isEqualTo("clinical_case");
        assertThat(saved.getValue().status()).isEqualTo("REQUESTED");
        assertThat(saved.getValue().requestedBy()).isEqualTo("doctor-1");
        assertThat(saved.getValue().exportScopeSnapshot()).contains("\"patientId\":\"p-1\"");

        ArgumentCaptor<AuditRecordCommand> audit = ArgumentCaptor.forClass(AuditRecordCommand.class);
        verify(auditRecorder).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.CREATE);
        assertThat(audit.getValue().targetType()).isEqualTo("mk_compliance_export_approval");
    }

    @Test
    void approveExportCreatesApprovalEvidenceAndReviewAudit() {
        ExportApproval existing = requestedApproval("exp-clinical-case-idem-001", "doctor-1");
        when(repository.findByTenantIdAndApprovalId("t-1", existing.approvalId()))
            .thenReturn(Optional.of(existing));
        when(evidenceService.createSnapshot(eq("t-1"), any(EvidenceCreateDto.class)))
            .thenReturn(evidence("evd-exp-clinical-case-idem-001-approval",
                "/api/v1/compliance/evidence/snapshots/evd-exp-clinical-case-idem-001-approval/file"));
        when(repository.save(any(ExportApproval.class)))
            .thenAnswer(invocation -> invocation.<ExportApproval>getArgument(0));
        ExportApprovalReviewRequest request = new ExportApprovalReviewRequest(
            ExportApprovalDecision.APPROVE,
            "审批通过，允许生成真实导出文件",
            1L);

        ExportApprovalResponse response = service.reviewExport("t-1", existing.approvalId(), request, "auditor-1");

        assertThat(response.status()).isEqualTo(ExportApprovalStatus.APPROVED);
        assertThat(response.approvalEvidenceId()).isEqualTo("evd-exp-clinical-case-idem-001-approval");
        ArgumentCaptor<EvidenceCreateDto> evidence = ArgumentCaptor.forClass(EvidenceCreateDto.class);
        verify(evidenceService).createSnapshot(eq("t-1"), evidence.capture());
        assertThat(evidence.getValue().evidenceType()).isEqualTo("COMPLIANCE_EXPORT_APPROVAL");
        assertThat(evidence.getValue().action()).isEqualTo("REVIEW");
        assertThat(evidence.getValue().subjectType()).isEqualTo("mk_compliance_export_approval");
        assertThat(evidence.getValue().payloadSnapshot())
            .contains("\"approvalId\":\"exp-clinical-case-idem-001\"")
            .contains("\"decision\":\"APPROVE\"");

        ArgumentCaptor<AuditRecordCommand> audit = ArgumentCaptor.forClass(AuditRecordCommand.class);
        verify(auditRecorder).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.REVIEW);
    }

    @Test
    void reviewExportRejectsSelfApproval() {
        ExportApproval existing = requestedApproval("exp-clinical-case-idem-001", "auditor-1");
        when(repository.findByTenantIdAndApprovalId("t-1", existing.approvalId()))
            .thenReturn(Optional.of(existing));
        ExportApprovalReviewRequest request = new ExportApprovalReviewRequest(
            ExportApprovalDecision.APPROVE,
            "审批通过",
            1L);

        ApiException ex = catchThrowableOfType(
            () -> service.reviewExport("t-1", existing.approvalId(), request, "auditor-1"),
            ApiException.class);

        assertThat(ex.errorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(ex.getMessage()).contains("申请人与审批人不能相同");
        verify(repository, never()).save(any());
        verify(evidenceService, never()).createSnapshot(any(), any());
    }

    @Test
    void completeExportRequiresApprovedRequestAndCreatesExportEvidence() {
        ExportApproval existing = approvedApproval("exp-clinical-case-idem-001");
        when(repository.findByTenantIdAndApprovalId("t-1", existing.approvalId()))
            .thenReturn(Optional.of(existing));
        when(evidenceService.createSnapshot(eq("t-1"), any(EvidenceCreateDto.class)))
            .thenReturn(evidence("evd-exp-clinical-case-idem-001-export",
                "/api/v1/compliance/evidence/snapshots/evd-exp-clinical-case-idem-001-export/file"));
        when(repository.save(any(ExportApproval.class)))
            .thenAnswer(invocation -> invocation.<ExportApproval>getArgument(0));
        ExportCompletionRequest request = new ExportCompletionRequest(
            "s3://tenant-t-1/exports/clinical-case.ndjson",
            DIGEST,
            "真实导出文件已生成并完成摘要登记",
            2L);

        ExportApprovalResponse response = service.completeExport("t-1", existing.approvalId(), request, "auditor-1");

        assertThat(response.status()).isEqualTo(ExportApprovalStatus.EXPORTED);
        assertThat(response.exportUri()).isEqualTo("s3://tenant-t-1/exports/clinical-case.ndjson");
        assertThat(response.exportEvidenceId()).isEqualTo("evd-exp-clinical-case-idem-001-export");
        ArgumentCaptor<EvidenceCreateDto> evidence = ArgumentCaptor.forClass(EvidenceCreateDto.class);
        verify(evidenceService).createSnapshot(eq("t-1"), evidence.capture());
        assertThat(evidence.getValue().evidenceType()).isEqualTo("COMPLIANCE_EXPORT");
        assertThat(evidence.getValue().action()).isEqualTo("EXPORT");
        assertThat(evidence.getValue().payloadSnapshot())
            .contains("\"exportUri\":\"s3://tenant-t-1/exports/clinical-case.ndjson\"")
            .contains("\"exportDigest\":\"" + DIGEST + "\"");

        ArgumentCaptor<AuditRecordCommand> audit = ArgumentCaptor.forClass(AuditRecordCommand.class);
        verify(auditRecorder).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.EXPORT);
    }

    private ExportApproval requestedApproval(String approvalId, String requestedBy) {
        Instant now = Instant.parse("2026-06-05T00:00:00Z");
        return new ExportApproval(
            1L,
            approvalId,
            "t-1",
            "clinical_case",
            "{\"patientId\":\"p-1\"}",
            "idem-001",
            "合规审计需要导出当前患者证据包",
            requestedBy,
            now,
            "REQUESTED",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            1L,
            now,
            requestedBy,
            now,
            requestedBy,
            "trace-test");
    }

    private ExportApproval approvedApproval(String approvalId) {
        ExportApproval requested = requestedApproval(approvalId, "doctor-1");
        Instant reviewedAt = Instant.parse("2026-06-05T01:00:00Z");
        return new ExportApproval(
            requested.id(),
            requested.approvalId(),
            requested.tenantId(),
            requested.resourceType(),
            requested.exportScopeSnapshot(),
            requested.idempotencyKey(),
            requested.requestReason(),
            requested.requestedBy(),
            requested.requestedAt(),
            "APPROVED",
            "auditor-1",
            "APPROVE",
            "审批通过，允许生成真实导出文件",
            reviewedAt,
            null,
            null,
            "evd-exp-clinical-case-idem-001-approval",
            "/api/v1/compliance/evidence/snapshots/evd-exp-clinical-case-idem-001-approval/file",
            null,
            null,
            2L,
            requested.createdAt(),
            requested.createdBy(),
            reviewedAt,
            "auditor-1",
            "trace-test");
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
            "mk_compliance_export_approval",
            "exp-clinical-case-idem-001",
            "合规导出审批证据",
            "{\"approvalId\":\"exp-clinical-case-idem-001\"}",
            "sm3:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            fileUri,
            "sm3:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
            "SM3_WITH_SM2",
            "sig",
            "pub",
            true,
            now,
            "system");
    }
}
