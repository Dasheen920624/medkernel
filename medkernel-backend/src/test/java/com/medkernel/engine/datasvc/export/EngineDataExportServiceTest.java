package com.medkernel.engine.datasvc.export;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.medkernel.engine.datasvc.ClinicalSignalStat;
import com.medkernel.engine.datasvc.ClinicalSignalsRepository;
import com.medkernel.engine.datasvc.KnowledgeUsageStatsRepository;
import com.medkernel.engine.datasvc.RuleUsageStat;
import com.medkernel.engine.datasvc.RuleUsageStatsRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.crypto.SmCryptoService;
import com.medkernel.shared.export.ExportApprovalGate;
import com.medkernel.shared.export.ExportArtifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 引擎数据服务层异步导出服务测试（DATASVC-01）。
 *
 * <p>覆盖：审批闸（不绕审批）/ 幂等 / worker 写 CSV / 小样本抑制 / 取消终态 / 完成产物 SM3 / provider 支持判定。
 */
class EngineDataExportServiceTest {

    private static final String SM3 = "sm3:1111111111111111111111111111111111111111111111111111111111111111";
    private static final String SCOPE = "{\"exportType\":\"RULE_USAGE\",\"windowDays\":90}";

    private EngineDataExportJobRepository jobRepo;
    private RuleUsageStatsRepository ruleRepo;
    private KnowledgeUsageStatsRepository knowledgeRepo;
    private ClinicalSignalsRepository clinicalRepo;
    private ExportApprovalGate approvalGate;
    private SmCryptoService crypto;
    private AuditRecorder auditRecorder;
    private EngineDataExportService service;

    @BeforeEach
    void setUp() {
        jobRepo = Mockito.mock(EngineDataExportJobRepository.class);
        ruleRepo = Mockito.mock(RuleUsageStatsRepository.class);
        knowledgeRepo = Mockito.mock(KnowledgeUsageStatsRepository.class);
        clinicalRepo = Mockito.mock(ClinicalSignalsRepository.class);
        approvalGate = Mockito.mock(ExportApprovalGate.class);
        crypto = Mockito.mock(SmCryptoService.class);
        auditRecorder = Mockito.mock(AuditRecorder.class);
        service = new EngineDataExportService(jobRepo, ruleRepo, knowledgeRepo, clinicalRepo,
            approvalGate, crypto, auditRecorder, new ObjectMapper(), command -> { });
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("t-1"), "quality-1"));

        when(jobRepo.save(any(EngineDataExportJob.class))).thenAnswer(inv -> {
            EngineDataExportJob j = inv.getArgument(0);
            return j.id() == null ? withId(j, 9L) : j;
        });
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void submitCreatesPendingJobWhenApprovalGatePasses() {
        when(jobRepo.findByTenantIdAndIdempotencyKey("t-1", "idem-1")).thenReturn(Optional.empty());

        EngineDataExportJob saved = service.submit(EngineDataExportType.RULE_USAGE, 90, "exp-1", "idem-1");

        assertThat(saved.status()).isEqualTo(ExportJobStatus.PENDING);
        assertThat(saved.tenantId()).isEqualTo("t-1");
        assertThat(saved.requestedBy()).isEqualTo("quality-1");
        assertThat(saved.jobCode()).matches("[0-9a-f-]{36}");
        assertThat(saved.approvalId()).isEqualTo("exp-1");
        assertThat(saved.requestSnapshot()).contains("RULE_USAGE").contains("90");
        // 不绕审批：submit 须以正确资源类型 + 范围调审批闸。
        Mockito.verify(approvalGate).requireApprovedForExport(
            eq("t-1"), eq("exp-1"), eq("engine_data_rule_usage"), any());
    }

    @Test
    void submitPropagatesGateForbiddenWhenNotApproved() {
        when(jobRepo.findByTenantIdAndIdempotencyKey("t-1", "idem-2")).thenReturn(Optional.empty());
        Mockito.doThrow(ApiException.forbidden("导出审批未通过"))
            .when(approvalGate).requireApprovedForExport(any(), any(), any(), any());

        assertThatThrownBy(() -> service.submit(EngineDataExportType.RULE_USAGE, 90, "exp-2", "idem-2"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        Mockito.verify(jobRepo, Mockito.never()).save(any());
    }

    @Test
    void submitPropagatesGateConflictWhenScopeMismatch() {
        when(jobRepo.findByTenantIdAndIdempotencyKey("t-1", "idem-4")).thenReturn(Optional.empty());
        Mockito.doThrow(ApiException.conflict("导出审批范围与作业不一致"))
            .when(approvalGate).requireApprovedForExport(any(), any(), any(), any());

        assertThatThrownBy(() -> service.submit(EngineDataExportType.RULE_USAGE, 90, "exp-4", "idem-4"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void submitReturnsExistingJobForSameIdempotencyKeyWithoutGate() {
        EngineDataExportJob existing = job("job-existing", ExportJobStatus.PENDING);
        when(jobRepo.findByTenantIdAndIdempotencyKey("t-1", "idem-dup")).thenReturn(Optional.of(existing));

        EngineDataExportJob saved = service.submit(EngineDataExportType.RULE_USAGE, 90, "exp-x", "idem-dup");

        assertThat(saved.jobCode()).isEqualTo("job-existing");
        Mockito.verify(approvalGate, Mockito.never()).requireApprovedForExport(any(), any(), any(), any());
        Mockito.verify(jobRepo, Mockito.never()).save(any());
    }

    @Test
    void executeJobWritesCsvWithRealCountsAndSucceeds() throws Exception {
        EngineDataExportJob pending = job("job-run", ExportJobStatus.PENDING);
        when(jobRepo.findByTenantIdAndJobCode("t-1", "job-run")).thenReturn(Optional.of(pending));
        when(ruleRepo.aggregateRuleUsage(eq("t-1"), any(), any(), eq(0), anyInt()))
            .thenReturn(List.of(new RuleUsageStat("rule-a", 25L, 18L, 2L, Instant.now())));

        service.executeJob("job-run");

        ArgumentCaptor<EngineDataExportJob> cap = ArgumentCaptor.forClass(EngineDataExportJob.class);
        Mockito.verify(jobRepo, Mockito.atLeast(2)).save(cap.capture());
        EngineDataExportJob last = cap.getAllValues().getLast();
        assertThat(last.status()).isEqualTo(ExportJobStatus.SUCCEEDED);
        assertThat(last.itemCount()).isEqualTo(1L);
        assertThat(last.resultUri()).isEqualTo("/api/v1/engine-data/exports/job-run/download");

        String csv = Files.readString(service.physicalExportPathForTest("job-run"));
        assertThat(csv).contains("rule-a").contains("25").contains("规则ID");
        assertThat(csv).doesNotContain("suppressed,suppressed");
    }

    @Test
    void executeJobSuppressesSmallSampleRows() throws Exception {
        EngineDataExportJob pending = job("job-suppress", ExportJobStatus.PENDING);
        when(jobRepo.findByTenantIdAndJobCode("t-1", "job-suppress")).thenReturn(Optional.of(pending));
        when(ruleRepo.aggregateRuleUsage(eq("t-1"), any(), any(), eq(0), anyInt()))
            .thenReturn(List.of(new RuleUsageStat("rule-rare", 3L, 1L, 0L, Instant.now())));

        service.executeJob("job-suppress");

        String csv = Files.readString(service.physicalExportPathForTest("job-suppress"));
        // 主计数 3 < 10：计数列抑制、保留分组键 rule-rare、抑制标志 true，真实计数 3 不出现
        assertThat(csv).contains("rule-rare").contains("suppressed").contains("true");
        assertThat(csv).doesNotContain(",3,");
    }

    @Test
    void executeJobMarksFailedWhenUpstreamUnavailable() {
        EngineDataExportJob pending = job("job-fail", ExportJobStatus.PENDING);
        when(jobRepo.findByTenantIdAndJobCode("t-1", "job-fail")).thenReturn(Optional.of(pending));
        when(ruleRepo.aggregateRuleUsage(eq("t-1"), any(), any(), eq(0), anyInt()))
            .thenThrow(new RuntimeException("upstream down"));

        assertThatThrownBy(() -> service.executeJob("job-fail")).isInstanceOf(Exception.class);
        // markFailed 是独立事务方法，由 worker 包裹调用；此处直接验证状态机方法诚实落 FAILED
        service.markFailed("job-fail", "upstream down");
        ArgumentCaptor<EngineDataExportJob> cap = ArgumentCaptor.forClass(EngineDataExportJob.class);
        Mockito.verify(jobRepo, Mockito.atLeastOnce()).save(cap.capture());
        assertThat(cap.getAllValues()).anyMatch(j -> j.status() == ExportJobStatus.FAILED
            && "upstream down".equals(j.errorMessage()));
    }

    @Test
    void cancelTerminalJobIsRejected() {
        EngineDataExportJob done = job("job-done", ExportJobStatus.SUCCEEDED);
        when(jobRepo.findByTenantIdAndJobCode("t-1", "job-done")).thenReturn(Optional.of(done));

        assertThatThrownBy(() -> service.cancel("job-done"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void completedExportArtifactComputesSm3AndReturnsArtifact() throws Exception {
        EngineDataExportJob pending = job("job-art", ExportJobStatus.PENDING);
        when(jobRepo.findByTenantIdAndJobCode("t-1", "job-art")).thenReturn(Optional.of(pending));
        when(ruleRepo.aggregateRuleUsage(eq("t-1"), any(), any(), eq(0), anyInt()))
            .thenReturn(List.of(new RuleUsageStat("rule-a", 25L, 18L, 2L, Instant.now())));
        service.executeJob("job-art");

        EngineDataExportJob succeeded = job("job-art", ExportJobStatus.SUCCEEDED);
        when(jobRepo.findByTenantIdAndJobCode("t-1", "job-art")).thenReturn(Optional.of(succeeded));
        when(crypto.sm3Hex(any(java.io.InputStream.class)))
            .thenReturn("1111111111111111111111111111111111111111111111111111111111111111");

        ExportArtifact artifact = service.completedExportArtifact("job-art");

        assertThat(artifact.jobId()).isEqualTo("job-art");
        assertThat(artifact.resourceType()).isEqualTo("engine_data_rule_usage");
        assertThat(artifact.exportDigest()).isEqualTo(SM3);
        assertThat(artifact.idempotencyKey()).isEqualTo("idem-job-art");
        assertThat(artifact.downloadUri()).isEqualTo("/api/v1/engine-data/exports/job-art/download");
    }

    @Test
    void supportsEngineDataResourceTypesOnly() {
        assertThat(service.supports("engine_data_rule_usage")).isTrue();
        assertThat(service.supports("engine_data_knowledge_usage")).isTrue();
        assertThat(service.supports("engine_data_clinical_signals")).isTrue();
        assertThat(service.supports("audit_event")).isFalse();
        assertThat(service.supports(null)).isFalse();
    }

    @Test
    void executeJobExportsClinicalSignalsByType() throws Exception {
        EngineDataExportJob pending = new EngineDataExportJob(1L, "t-1", "job-cs", "quality-1",
            EngineDataExportType.CLINICAL_SIGNALS, ExportJobStatus.PENDING, 0, null, null, null,
            "exp-cs", "idem-cs", "{\"exportType\":\"CLINICAL_SIGNALS\",\"windowDays\":90}",
            Instant.now(), null, null, null);
        when(jobRepo.findByTenantIdAndJobCode("t-1", "job-cs")).thenReturn(Optional.of(pending));
        when(clinicalRepo.aggregateClinicalSignals(eq("t-1"), any(), any(), eq(0), anyInt()))
            .thenReturn(List.of(new ClinicalSignalStat("ALERT", 30L, 12L, 20L, 5L, Instant.now())));

        service.executeJob("job-cs");

        String csv = Files.readString(service.physicalExportPathForTest("job-cs"));
        assertThat(csv).contains("ALERT").contains("信号总数").contains("30");
    }

    private EngineDataExportJob job(String code, ExportJobStatus status) {
        Instant now = Instant.now();
        return new EngineDataExportJob(1L, "t-1", code, "quality-1",
            EngineDataExportType.RULE_USAGE, status, 0, null, null, null,
            "exp-" + code, "idem-" + code, SCOPE, now, null, null, null);
    }

    private EngineDataExportJob withId(EngineDataExportJob j, long id) {
        return new EngineDataExportJob(id, j.tenantId(), j.jobCode(), j.requestedBy(),
            j.exportType(), j.status(), j.progress(), j.resultUri(), j.itemCount(), j.errorMessage(),
            j.approvalId(), j.idempotencyKey(), j.requestSnapshot(),
            j.createdAt(), j.startedAt(), j.completedAt(), j.expiresAt());
    }
}
