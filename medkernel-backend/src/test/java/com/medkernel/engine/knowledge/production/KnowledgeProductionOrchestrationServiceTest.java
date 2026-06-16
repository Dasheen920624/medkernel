package com.medkernel.engine.knowledge.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.medkernel.engine.factory.AssetSourceRef;
import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.factory.KnowledgeAssetSchemaValidator;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.hash.Sha256ContentHash;

/**
 * 知识生产编排服务单元测试（AIK-STD-13）。
 *
 * <p>验证：FR-1 job 骨架、FR-4 双形态物理隔离守卫（PLATFORM_SOURCE 仅平台租户 / TENANT_OVERLAY 仅客户租户 /
 * 覆盖候选禁反写 t-1 主源 → {@code KNOWLEDGE_PRODUCTION_PIPELINE_VIOLATION}）、FR-3 候选经校验闸 + 隔离 + 计数、
 * FR-5 血缘审计；候选物化经 {@link KnowledgeCandidateIntake} 端口。
 */
class KnowledgeProductionOrchestrationServiceTest {

    private static final String CUSTOMER = "tenant-1";

    private KnowledgeProductionJobRepository jobRepository;
    private KnowledgeProductionCandidateRepository candidateRepository;
    private KnowledgeCandidateIntake candidateIntake;
    private AuditRecorder auditRecorder;
    private KnowledgeProductionOrchestrationService service;

    @BeforeEach
    void setUp() {
        jobRepository = mock(KnowledgeProductionJobRepository.class);
        candidateRepository = mock(KnowledgeProductionCandidateRepository.class);
        candidateIntake = mock(KnowledgeCandidateIntake.class);
        auditRecorder = mock(AuditRecorder.class);
        service = new KnowledgeProductionOrchestrationService(
            jobRepository, candidateRepository, candidateIntake, new KnowledgeAssetSchemaValidator(),
            auditRecorder, new CandidateReviewRouter());
        when(jobRepository.save(any())).thenAnswer(inv -> {
            KnowledgeProductionJob j = inv.getArgument(0);
            return j.id() == null
                ? new KnowledgeProductionJob(1L, j.tenantId(), j.jobCode(), j.sourceScope(), j.assetType(),
                    j.producer(), j.targetPipeline(), j.domain(), j.modelStrategy(), j.status(),
                    j.candidateCount(), j.lineage(), j.createdAt(), j.createdBy(), j.updatedAt(),
                    j.updatedBy(), j.traceId())
                : j;
        });
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private void asTenant(String tenantId) {
        RequestContext.restore(new RequestContext.Snapshot("trace-1", OrgScope.tenant(tenantId), "user-001"));
    }

    private ProductionJobRequest request(TargetPipeline pipeline) {
        return new ProductionJobRequest("探索 run r-1", VersionedAssetType.KNOWLEDGE,
            KnowledgeProducer.MANUAL, pipeline, KnowledgeDomain.GENERAL, null);
    }

    private KnowledgeAssetEnvelope envelope(String orgScope, VersionedAssetType type) {
        String payload = "受控候选正文";
        return new KnowledgeAssetEnvelope(type, "discovery:SRC:v1:a", "主题", "run-1",
            List.of(new AssetSourceRef("SRC:v1:a", SourceAuthorityLevel.A_REGULATION)),
            SourceAuthorityLevel.A_REGULATION, null, null, KnowledgeRiskLevel.MEDIUM, orgScope,
            Sha256ContentHash.sha256(payload, "x"), payload, AssetVersionStatus.DRAFT);
    }

    private KnowledgeProductionJob overlayJob(String tenantId, VersionedAssetType type) {
        return new KnowledgeProductionJob(1L, tenantId, "job-1", "探索 run r-1", type,
            KnowledgeProducer.MANUAL, TargetPipeline.TENANT_OVERLAY, KnowledgeDomain.GENERAL, null,
            ProductionJobStatus.PENDING, 0, null, java.time.Instant.now(), "user-001",
            java.time.Instant.now(), "user-001", "trace-1");
    }

    // ─── FR-4 双形态隔离守卫（createJob）─────────────────────────

    @Test
    void createPlatformSourceJobAsPlatformTenantSucceeds() {
        asTenant(PlatformTenant.ID);

        ProductionJobResponse response = service.createJob(request(TargetPipeline.PLATFORM_SOURCE));

        assertThat(response.targetPipeline()).isEqualTo(TargetPipeline.PLATFORM_SOURCE);
        assertThat(response.status()).isEqualTo(ProductionJobStatus.PENDING);
        assertThat(response.jobCode()).isNotBlank();
        assertThat(response.tenantId()).isEqualTo(PlatformTenant.ID);
    }

    @Test
    void createPlatformSourceJobAsCustomerTenantIsPipelineViolation() {
        asTenant(CUSTOMER);

        assertThatThrownBy(() -> service.createJob(request(TargetPipeline.PLATFORM_SOURCE)))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.KNOWLEDGE_PRODUCTION_PIPELINE_VIOLATION);
        verify(jobRepository, never()).save(any());
    }

    @Test
    void createTenantOverlayJobAsPlatformTenantIsPipelineViolation() {
        asTenant(PlatformTenant.ID);

        assertThatThrownBy(() -> service.createJob(request(TargetPipeline.TENANT_OVERLAY)))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.KNOWLEDGE_PRODUCTION_PIPELINE_VIOLATION);
    }

    @Test
    void createTenantOverlayJobAsCustomerTenantSucceeds() {
        asTenant(CUSTOMER);

        ProductionJobResponse response = service.createJob(request(TargetPipeline.TENANT_OVERLAY));

        assertThat(response.targetPipeline()).isEqualTo(TargetPipeline.TENANT_OVERLAY);
        assertThat(response.producer()).isEqualTo(KnowledgeProducer.MANUAL);
        assertThat(response.status()).isEqualTo(ProductionJobStatus.PENDING);
    }

    @Test
    void createJobPersistsDeclaredDomain() {
        asTenant(CUSTOMER);
        ProductionJobRequest req = new ProductionJobRequest("探索 run r-1", VersionedAssetType.RULE,
            KnowledgeProducer.MANUAL, TargetPipeline.TENANT_OVERLAY, KnowledgeDomain.PHARMACY, null);

        ProductionJobResponse response = service.createJob(req);

        assertThat(response.domain()).isEqualTo(KnowledgeDomain.PHARMACY);
    }

    // ─── FR-3/4/5 submitCandidate ──────────────────────────────

    @Test
    void submitCandidateValidatesIsolatesCountsAndAudits() {
        asTenant(CUSTOMER);
        when(jobRepository.findByTenantIdAndJobCode(CUSTOMER, "job-1"))
            .thenReturn(Optional.of(overlayJob(CUSTOMER, VersionedAssetType.KNOWLEDGE)));
        when(candidateIntake.intake(any(), any(), any(), any())).thenReturn("staged:discovery:SRC:v1:a");

        CandidateSubmissionResponse resp = service.submitCandidate("job-1",
            envelope(CUSTOMER, VersionedAssetType.KNOWLEDGE), new MaterializationTarget(5L, null));

        assertThat(resp.candidateRef()).isEqualTo("staged:discovery:SRC:v1:a");
        assertThat(resp.routing().ownerReviewerRole())
            .isEqualTo(com.medkernel.engine.security.RoleCode.KNOWLEDGE_GOVERNOR); // overlay→机构归口
        verify(candidateIntake).intake(any(), any(), any(), any());
        ArgumentCaptor<KnowledgeProductionJob> saved = ArgumentCaptor.forClass(KnowledgeProductionJob.class);
        verify(jobRepository).save(saved.capture());
        assertThat(saved.getValue().candidateCount()).isEqualTo(1);
        assertThat(saved.getValue().status()).isEqualTo(ProductionJobStatus.RUNNING);
        verify(auditRecorder).record(eq(AuditAction.EXECUTE), eq("mk_knowledge_production_job"),
            anyString(), anyString());
    }

    @Test
    void submitCandidateRejectsOverlayWriteBackToPlatformSource() {
        asTenant(CUSTOMER);
        when(jobRepository.findByTenantIdAndJobCode(CUSTOMER, "job-1"))
            .thenReturn(Optional.of(overlayJob(CUSTOMER, VersionedAssetType.KNOWLEDGE)));

        assertThatThrownBy(() ->
            service.submitCandidate("job-1", envelope(PlatformTenant.ID, VersionedAssetType.KNOWLEDGE),
                new MaterializationTarget(5L, null)))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.KNOWLEDGE_PRODUCTION_PIPELINE_VIOLATION);
        verify(candidateIntake, never()).intake(any(), any(), any(), any());
    }

    @Test
    void submitCandidateRejectsAssetTypeMismatch() {
        asTenant(CUSTOMER);
        when(jobRepository.findByTenantIdAndJobCode(CUSTOMER, "job-1"))
            .thenReturn(Optional.of(overlayJob(CUSTOMER, VersionedAssetType.KNOWLEDGE)));

        assertThatThrownBy(() ->
            service.submitCandidate("job-1", envelope(CUSTOMER, VersionedAssetType.RULE),
                new MaterializationTarget(5L, null)))
            .isInstanceOf(ApiException.class);
        verify(candidateIntake, never()).intake(any(), any(), any(), any());
    }

    @Test
    void submitCandidateRejectsForeignTenantCandidate() {
        asTenant(CUSTOMER);
        when(jobRepository.findByTenantIdAndJobCode(CUSTOMER, "job-1"))
            .thenReturn(Optional.of(overlayJob(CUSTOMER, VersionedAssetType.KNOWLEDGE)));

        assertThatThrownBy(() ->
            service.submitCandidate("job-1", envelope("tenant-2", VersionedAssetType.KNOWLEDGE),
                new MaterializationTarget(5L, null)))
            .isInstanceOf(ApiException.class);
        verify(candidateIntake, never()).intake(any(), any(), any(), any());
    }

    @Test
    void submitCandidateRejectsInvalidEnvelopeViaSchemaGate() {
        asTenant(CUSTOMER);
        when(jobRepository.findByTenantIdAndJobCode(CUSTOMER, "job-1"))
            .thenReturn(Optional.of(overlayJob(CUSTOMER, VersionedAssetType.KNOWLEDGE)));
        // 无来源信封：经 AIK-STD-01 校验闸拒收
        KnowledgeAssetEnvelope noSource = new KnowledgeAssetEnvelope(VersionedAssetType.KNOWLEDGE,
            "id", "主题", "run-1", List.of(), SourceAuthorityLevel.A_REGULATION, null, null,
            KnowledgeRiskLevel.MEDIUM, CUSTOMER, Sha256ContentHash.sha256("x", "x"), "x",
            AssetVersionStatus.DRAFT);

        assertThatThrownBy(() -> service.submitCandidate("job-1", noSource, new MaterializationTarget(5L, null)))
            .isInstanceOf(ApiException.class);
        verify(candidateIntake, never()).intake(any(), any(), any(), any());
    }

    @Test
    void submitCandidateUnknownJobIsNotFound() {
        asTenant(CUSTOMER);
        when(jobRepository.findByTenantIdAndJobCode(CUSTOMER, "missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service.submitCandidate("missing", envelope(CUSTOMER, VersionedAssetType.KNOWLEDGE),
                new MaterializationTarget(5L, null)))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void listJobsReturnsTenantScopedPage() {
        asTenant(CUSTOMER);
        KnowledgeProductionJob job = overlayJob(CUSTOMER, VersionedAssetType.KNOWLEDGE);
        when(jobRepository.countByTenantId(CUSTOMER)).thenReturn(21L);
        when(jobRepository.pageByTenantId(eq(CUSTOMER), eq(0), eq(20))).thenReturn(List.of(job));

        PageResponse<KnowledgeProductionJob> jobs = service.listJobs(1, 20);

        assertThat(jobs.items()).containsExactly(job);
        assertThat(jobs.page()).isEqualTo(1);
        assertThat(jobs.size()).isEqualTo(20);
        assertThat(jobs.total()).isEqualTo(21);
        assertThat(jobs.hasNext()).isTrue();
        verify(jobRepository).countByTenantId(CUSTOMER);
        verify(jobRepository).pageByTenantId(CUSTOMER, 0, 20);
    }

    // ─── PR2：候选生产血缘 + job 生命周期 ─────────────────────────

    private KnowledgeProductionJob jobWith(String tenantId, ProductionJobStatus status, TargetPipeline pipeline) {
        return new KnowledgeProductionJob(1L, tenantId, "job-1", "探索 run r-1",
            VersionedAssetType.KNOWLEDGE, KnowledgeProducer.MANUAL, pipeline, KnowledgeDomain.GENERAL,
            "strat-1", status, 0, "{\"producer\":\"MANUAL\"}", java.time.Instant.now(), "user-001",
            java.time.Instant.now(), "user-001", "trace-1");
    }

    @Test
    void submitCandidatePersistsProductionLineageRow() {
        asTenant(CUSTOMER);
        when(jobRepository.findByTenantIdAndJobCode(CUSTOMER, "job-1"))
            .thenReturn(Optional.of(overlayJob(CUSTOMER, VersionedAssetType.KNOWLEDGE)));
        when(candidateIntake.intake(any(), any(), any(), any())).thenReturn("staged:discovery:SRC:v1:a");
        KnowledgeAssetEnvelope candidate = envelope(CUSTOMER, VersionedAssetType.KNOWLEDGE);

        service.submitCandidate("job-1", candidate, new MaterializationTarget(5L, null));

        ArgumentCaptor<KnowledgeProductionCandidate> lineage =
            ArgumentCaptor.forClass(KnowledgeProductionCandidate.class);
        verify(candidateRepository).save(lineage.capture());
        assertThat(lineage.getValue().jobCode()).isEqualTo("job-1");
        assertThat(lineage.getValue().assetIdentity()).isEqualTo(candidate.assetIdentity());
        assertThat(lineage.getValue().contentHash()).isEqualTo(candidate.contentHash());
        assertThat(lineage.getValue().candidateRef()).isEqualTo("staged:discovery:SRC:v1:a");
        assertThat(lineage.getValue().tenantId()).isEqualTo(CUSTOMER);
        assertThat(lineage.getValue().riskLevel()).isEqualTo(KnowledgeRiskLevel.MEDIUM);
    }

    @Test
    void listCandidatesReturnsTenantScopedPageWithRouting() {
        asTenant(CUSTOMER);
        when(jobRepository.findByTenantIdAndJobCode(CUSTOMER, "job-1"))
            .thenReturn(Optional.of(overlayJob(CUSTOMER, VersionedAssetType.KNOWLEDGE)));
        KnowledgeProductionCandidate row = new KnowledgeProductionCandidate(5L, CUSTOMER, "job-1",
            "discovery:SRC:v1:a", "hash", "staged:x",
            KnowledgeRiskLevel.HIGH, java.time.Instant.now(), "user-001");
        when(candidateRepository.countByTenantIdAndJobCode(CUSTOMER, "job-1")).thenReturn(21L);
        when(candidateRepository.pageByTenantIdAndJobCode(CUSTOMER, "job-1", 0, 20)).thenReturn(List.of(row));

        PageResponse<ProductionCandidateView> views = service.listCandidates("job-1", 1, 20);

        assertThat(views.items()).hasSize(1);
        assertThat(views.page()).isEqualTo(1);
        assertThat(views.size()).isEqualTo(20);
        assertThat(views.total()).isEqualTo(21);
        assertThat(views.hasNext()).isTrue();
        assertThat(views.items().get(0).candidateRef()).isEqualTo("staged:x");
        assertThat(views.items().get(0).routing().requiresDualSign()).isTrue(); // HIGH→双签
        assertThat(views.items().get(0).routing().ownerReviewerRole())
            .isEqualTo(com.medkernel.engine.security.RoleCode.KNOWLEDGE_GOVERNOR);
        verify(candidateRepository).countByTenantIdAndJobCode(CUSTOMER, "job-1");
        verify(candidateRepository).pageByTenantIdAndJobCode(CUSTOMER, "job-1", 0, 20);
    }

    @Test
    void completeJobTransitionsToCompleted() {
        asTenant(CUSTOMER);
        when(jobRepository.findByTenantIdAndJobCode(CUSTOMER, "job-1"))
            .thenReturn(Optional.of(jobWith(CUSTOMER, ProductionJobStatus.RUNNING, TargetPipeline.TENANT_OVERLAY)));

        ProductionJobResponse response = service.completeJob("job-1");

        assertThat(response.status()).isEqualTo(ProductionJobStatus.COMPLETED);
    }

    @Test
    void cancelJobTransitionsToCancelled() {
        asTenant(CUSTOMER);
        when(jobRepository.findByTenantIdAndJobCode(CUSTOMER, "job-1"))
            .thenReturn(Optional.of(jobWith(CUSTOMER, ProductionJobStatus.PENDING, TargetPipeline.TENANT_OVERLAY)));

        ProductionJobResponse response = service.cancelJob("job-1");

        assertThat(response.status()).isEqualTo(ProductionJobStatus.CANCELLED);
    }

    @Test
    void cancelJobOnTerminalStateIsRejected() {
        asTenant(CUSTOMER);
        when(jobRepository.findByTenantIdAndJobCode(CUSTOMER, "job-1"))
            .thenReturn(Optional.of(jobWith(CUSTOMER, ProductionJobStatus.COMPLETED, TargetPipeline.TENANT_OVERLAY)));

        assertThatThrownBy(() -> service.cancelJob("job-1")).isInstanceOf(ApiException.class);
    }

    @Test
    void replayJobSpawnsNewPendingJobLinkedToOriginal() {
        asTenant(CUSTOMER);
        when(jobRepository.findByTenantIdAndJobCode(CUSTOMER, "job-1"))
            .thenReturn(Optional.of(jobWith(CUSTOMER, ProductionJobStatus.COMPLETED, TargetPipeline.TENANT_OVERLAY)));

        ProductionJobResponse response = service.replayJob("job-1");

        ArgumentCaptor<KnowledgeProductionJob> saved = ArgumentCaptor.forClass(KnowledgeProductionJob.class);
        verify(jobRepository).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(ProductionJobStatus.PENDING);
        assertThat(saved.getValue().jobCode()).isNotEqualTo("job-1");
        assertThat(saved.getValue().targetPipeline()).isEqualTo(TargetPipeline.TENANT_OVERLAY);
        assertThat(saved.getValue().lineage()).contains("replayedFrom").contains("job-1");
        assertThat(response.status()).isEqualTo(ProductionJobStatus.PENDING);
    }
}
