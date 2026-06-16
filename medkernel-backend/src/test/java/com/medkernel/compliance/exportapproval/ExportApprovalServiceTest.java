package com.medkernel.compliance.exportapproval;

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
import com.medkernel.engine.list.LargeListEngineService;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
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

class ExportApprovalServiceTest {

    private static final String DIGEST = "sm3:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private ExportApprovalRepository repository;
    private EvidenceService evidenceService;
    private AuditRecorder auditRecorder;
    private LargeListEngineService largeListEngineService;
    private ExportApprovalService service;

    @BeforeEach
    void setUp() {
        repository = mock(ExportApprovalRepository.class);
        evidenceService = mock(EvidenceService.class);
        auditRecorder = mock(AuditRecorder.class);
        largeListEngineService = mock(LargeListEngineService.class);
        service = new ExportApprovalService(
            repository,
            evidenceService,
            auditRecorder,
            List.of(largeListEngineService),
            new ObjectMapper());
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
    void listApprovalsReturnsTenantScopedRowsFilteredByStatusAndResource() {
        ExportApproval requested = requestedApproval("exp-audit-event-idem-001", "auditor-1");
        when(repository.countByFilter("t-1", "audit_event", "REQUESTED"))
            .thenReturn(1L);
        when(repository.pageByFilter(
            "t-1", "audit_event", "REQUESTED", 0, 20))
            .thenReturn(List.of(requested));

        PageResponse<ExportApprovalResponse> responses = service.listApprovals(
            "t-1", "AUDIT_EVENT", ExportApprovalStatus.REQUESTED, PageRequest.defaults());

        assertThat(responses.total()).isEqualTo(1L);
        assertThat(responses.items()).extracting(ExportApprovalResponse::approvalId)
            .containsExactly("exp-audit-event-idem-001");
        assertThat(responses.items().getFirst().requestReason())
            .isEqualTo("合规审计需要导出当前患者证据包");
        verify(repository).countByFilter("t-1", "audit_event", "REQUESTED");
        verify(repository).pageByFilter("t-1", "audit_event", "REQUESTED", 0, 20);
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
    void approveExportUsesBoundedEvidenceIdForLongApprovalId() {
        ExportApproval existing = requestedApproval(
            "exp-act10-patient-export-act10-mq8waf17.patient-export",
            "doctor-1");
        when(repository.findByTenantIdAndApprovalId("t-1", existing.approvalId()))
            .thenReturn(Optional.of(existing));
        when(evidenceService.createSnapshot(eq("t-1"), any(EvidenceCreateDto.class)))
            .thenAnswer(invocation -> {
                EvidenceCreateDto dto = invocation.getArgument(1);
                return evidence(dto.evidenceId(),
                    "/api/v1/compliance/evidence/snapshots/" + dto.evidenceId() + "/file");
            });
        when(repository.save(any(ExportApproval.class)))
            .thenAnswer(invocation -> invocation.<ExportApproval>getArgument(0));
        ExportApprovalReviewRequest request = new ExportApprovalReviewRequest(
            ExportApprovalDecision.APPROVE,
            "审批通过，允许生成真实导出文件",
            1L);

        ExportApprovalResponse response = service.reviewExport("t-1", existing.approvalId(), request, "auditor-1");

        ArgumentCaptor<EvidenceCreateDto> evidence = ArgumentCaptor.forClass(EvidenceCreateDto.class);
        verify(evidenceService).createSnapshot(eq("t-1"), evidence.capture());
        assertThat(evidence.getValue().evidenceId())
            .startsWith("evd-exp-act10-patient-export-")
            .endsWith("-approval")
            .hasSizeLessThanOrEqualTo(64);
        assertThat(response.approvalEvidenceId()).isEqualTo(evidence.getValue().evidenceId());
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
    void completeExportFromJobRequiresMatchingApprovedArtifactAndCreatesEvidence() {
        ExportApproval existing = approvedAuditApproval();
        when(repository.findByTenantIdAndApprovalId("t-1", existing.approvalId()))
            .thenReturn(Optional.of(existing));
        when(largeListEngineService.supports("audit_event")).thenReturn(true);
        when(largeListEngineService.completedExportArtifact("job-audit-1"))
            .thenReturn(new ExportArtifact(
                "job-audit-1",
                "AUDIT_EVENT",
                existing.exportScopeSnapshot(),
                existing.idempotencyKey(),
                "/medkernel/api/v1/large-lists/exports/job-audit-1/download",
                DIGEST));
        when(evidenceService.createSnapshot(eq("t-1"), any(EvidenceCreateDto.class)))
            .thenReturn(evidence("evd-exp-audit-event-idem-001-export",
                "/api/v1/compliance/evidence/snapshots/evd-exp-audit-event-idem-001-export/file"));
        when(repository.save(any(ExportApproval.class)))
            .thenAnswer(invocation -> invocation.<ExportApproval>getArgument(0));
        ExportJobCompletionRequest request = new ExportJobCompletionRequest(
            "job-audit-1", "真实导出文件已生成并完成摘要登记", 2L);

        ExportApprovalResponse response = service.completeExportFromJob(
            "t-1", existing.approvalId(), request, "auditor-1");

        assertThat(response.status()).isEqualTo(ExportApprovalStatus.EXPORTED);
        assertThat(response.exportUri())
            .isEqualTo("/medkernel/api/v1/large-lists/exports/job-audit-1/download");
        assertThat(response.exportDigest()).isEqualTo(DIGEST);
        assertThat(response.exportEvidenceId()).isEqualTo("evd-exp-audit-event-idem-001-export");
        ArgumentCaptor<EvidenceCreateDto> evidence = ArgumentCaptor.forClass(EvidenceCreateDto.class);
        verify(evidenceService).createSnapshot(eq("t-1"), evidence.capture());
        assertThat(evidence.getValue().evidenceType()).isEqualTo("COMPLIANCE_EXPORT");
        assertThat(evidence.getValue().action()).isEqualTo("EXPORT");
        assertThat(evidence.getValue().payloadSnapshot())
            .contains("\"jobId\":\"job-audit-1\"")
            .contains("\"exportUri\":\"/medkernel/api/v1/large-lists/exports/job-audit-1/download\"")
            .contains("\"exportDigest\":\"" + DIGEST + "\"");

        ArgumentCaptor<AuditRecordCommand> audit = ArgumentCaptor.forClass(AuditRecordCommand.class);
        verify(auditRecorder).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.EXPORT);
    }

    @Test
    void completeExportFromJobRejectsArtifactOutsideApprovedScope() {
        ExportApproval existing = approvedAuditApproval();
        when(repository.findByTenantIdAndApprovalId("t-1", existing.approvalId()))
            .thenReturn(Optional.of(existing));
        when(largeListEngineService.supports("audit_event")).thenReturn(true);
        when(largeListEngineService.completedExportArtifact("job-audit-2"))
            .thenReturn(new ExportArtifact(
                "job-audit-2",
                "AUDIT_EVENT",
                "{\"resourceType\":\"AUDIT_EVENT\",\"filters\":{\"action\":\"LOGIN\"},\"selectedScope\":\"FILTERED_RESULT\"}",
                existing.idempotencyKey(),
                "/medkernel/api/v1/large-lists/exports/job-audit-2/download",
                DIGEST));

        ApiException exception = catchThrowableOfType(
            () -> service.completeExportFromJob(
                "t-1",
                existing.approvalId(),
                new ExportJobCompletionRequest("job-audit-2", "登记导出", 2L),
                "auditor-1"),
            ApiException.class);

        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(exception.getMessage()).contains("审批范围");
        verify(repository, never()).save(any());
        verify(evidenceService, never()).createSnapshot(any(), any());
    }

    @Test
    void completeExportFromJobResolvesProviderByResourceTypeAmongMany() {
        ExportArtifactProvider engineDataProvider = mock(ExportArtifactProvider.class);
        when(largeListEngineService.supports("engine_data_rule_usage")).thenReturn(false);
        when(engineDataProvider.supports("engine_data_rule_usage")).thenReturn(true);
        ExportApprovalService twoProviderService = new ExportApprovalService(
            repository,
            evidenceService,
            auditRecorder,
            List.of(largeListEngineService, engineDataProvider),
            new ObjectMapper());

        ExportApproval existing = approvedEngineDataApproval();
        when(repository.findByTenantIdAndApprovalId("t-1", existing.approvalId()))
            .thenReturn(Optional.of(existing));
        when(engineDataProvider.completedExportArtifact("job-ed-1"))
            .thenReturn(new ExportArtifact(
                "job-ed-1",
                "engine_data_rule_usage",
                existing.exportScopeSnapshot(),
                existing.idempotencyKey(),
                "/api/v1/engine-data/exports/job-ed-1/download",
                DIGEST));
        when(evidenceService.createSnapshot(eq("t-1"), any(EvidenceCreateDto.class)))
            .thenReturn(evidence("evd-exp-engine-data-rule-usage-idem-001-export",
                "/api/v1/compliance/evidence/snapshots/evd-exp-engine-data-rule-usage-idem-001-export/file"));
        when(repository.save(any(ExportApproval.class)))
            .thenAnswer(invocation -> invocation.<ExportApproval>getArgument(0));

        ExportApprovalResponse response = twoProviderService.completeExportFromJob(
            "t-1",
            existing.approvalId(),
            new ExportJobCompletionRequest("job-ed-1", "登记 engine-data 导出完成", 2L),
            "auditor-1");

        assertThat(response.status()).isEqualTo(ExportApprovalStatus.EXPORTED);
        assertThat(response.exportUri()).isEqualTo("/api/v1/engine-data/exports/job-ed-1/download");
        verify(engineDataProvider).completedExportArtifact("job-ed-1");
        verify(largeListEngineService, never()).completedExportArtifact(any());
    }

    @Test
    void completeExportFromJobRejectsWhenNoProviderSupportsResourceType() {
        when(largeListEngineService.supports(any())).thenReturn(false);
        ExportApproval existing = approvedEngineDataApproval();
        when(repository.findByTenantIdAndApprovalId("t-1", existing.approvalId()))
            .thenReturn(Optional.of(existing));

        ApiException exception = catchThrowableOfType(
            () -> service.completeExportFromJob(
                "t-1",
                existing.approvalId(),
                new ExportJobCompletionRequest("job-ed-x", "登记导出", 2L),
                "auditor-1"),
            ApiException.class);

        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(exception.getMessage()).contains("资源类型");
        verify(repository, never()).save(any());
        verify(evidenceService, never()).createSnapshot(any(), any());
    }

    private ExportApproval approvedEngineDataApproval() {
        ExportApproval requested = requestedApproval("exp-engine-data-rule-usage-idem-001", "requester-1");
        Instant reviewedAt = Instant.parse("2026-06-05T01:00:00Z");
        return new ExportApproval(
            requested.id(),
            requested.approvalId(),
            requested.tenantId(),
            "engine_data_rule_usage",
            "{\"exportType\":\"RULE_USAGE\",\"windowDays\":90}",
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
            "evd-exp-engine-data-rule-usage-idem-001-approval",
            "/api/v1/compliance/evidence/snapshots/evd-exp-engine-data-rule-usage-idem-001-approval/file",
            null,
            null,
            2L,
            requested.createdAt(),
            requested.createdBy(),
            reviewedAt,
            "auditor-1",
            "trace-test");
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

    private ExportApproval approvedAuditApproval() {
        ExportApproval requested = requestedApproval("exp-audit-event-idem-001", "requester-1");
        Instant reviewedAt = Instant.parse("2026-06-05T01:00:00Z");
        return new ExportApproval(
            requested.id(),
            requested.approvalId(),
            requested.tenantId(),
            "audit_event",
            "{\"filters\":{\"action\":\"EXPORT\"},\"resourceType\":\"AUDIT_EVENT\",\"selectedScope\":\"FILTERED_RESULT\"}",
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
            "evd-exp-audit-event-idem-001-approval",
            "/api/v1/compliance/evidence/snapshots/evd-exp-audit-event-idem-001-approval/file",
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
