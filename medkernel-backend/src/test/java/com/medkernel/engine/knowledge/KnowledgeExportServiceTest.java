package com.medkernel.engine.knowledge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class KnowledgeExportServiceTest {

    private KnowledgeExportJobRepository jobRepo;
    private KnowledgeIdentityRepository identityRepo;
    private KnowledgeAssetVersionRepository versionRepo;
    private KnowledgeSupersessionRepository supersessionRepo;
    private CitationRepository citationRepo;
    private KnowledgeInvalidationRepository invalidationRepo;
    private AffectedCaseTaskRepository affectedCaseTaskRepo;
    private KnowledgeExportService service;

    @BeforeEach
    void setUp() {
        jobRepo = Mockito.mock(KnowledgeExportJobRepository.class);
        identityRepo = Mockito.mock(KnowledgeIdentityRepository.class);
        versionRepo = Mockito.mock(KnowledgeAssetVersionRepository.class);
        supersessionRepo = Mockito.mock(KnowledgeSupersessionRepository.class);
        citationRepo = Mockito.mock(CitationRepository.class);
        invalidationRepo = Mockito.mock(KnowledgeInvalidationRepository.class);
        affectedCaseTaskRepo = Mockito.mock(AffectedCaseTaskRepository.class);
        service = new KnowledgeExportService(jobRepo, identityRepo, versionRepo,
            supersessionRepo, citationRepo, invalidationRepo, affectedCaseTaskRepo, objectMapper(), command -> { });
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("t-1"), "u-99"));

        when(jobRepo.save(any(KnowledgeExportJob.class))).thenAnswer(inv -> {
            KnowledgeExportJob j = inv.getArgument(0);
            return j.id() == null
                ? new KnowledgeExportJob(99L, j.tenantId(), j.jobCode(), j.requestedBy(),
                    j.exportType(), j.filterJson(), j.status(), j.progress(),
                    j.resultUri(), j.itemCount(), j.errorMessage(),
                    j.createdAt(), j.startedAt(), j.completedAt(), j.expiresAt())
                : j;
        });
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void submitCreatesPendingJobWithUuidJobCode() {
        KnowledgeExportJob saved = service.submit(ExportType.IDENTITIES, "{\"domain\":\"DRUG\"}");

        assertThat(saved.status()).isEqualTo(ExportStatus.PENDING);
        assertThat(saved.tenantId()).isEqualTo("t-1");
        assertThat(saved.requestedBy()).isEqualTo("u-99");
        assertThat(saved.jobCode()).matches("[0-9a-f-]{36}");
        assertThat(saved.progress()).isZero();
        assertThat(saved.exportType()).isEqualTo(ExportType.IDENTITIES);

        ArgumentCaptor<KnowledgeExportJob> cap = ArgumentCaptor.forClass(KnowledgeExportJob.class);
        Mockito.verify(jobRepo).save(cap.capture());
        assertThat(cap.getValue().filterJson()).isEqualTo("{\"domain\":\"DRUG\"}");
    }

    @Test
    void submitDefersWorkerDispatchUntilCommit() {
        List<Runnable> dispatched = new ArrayList<>();
        service = new KnowledgeExportService(jobRepo, identityRepo, versionRepo,
            supersessionRepo, citationRepo, invalidationRepo, affectedCaseTaskRepo, objectMapper(), dispatched::add);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.submit(ExportType.IDENTITIES, null);

            assertThat(dispatched).isEmpty();
            TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
            assertThat(dispatched).hasSize(1);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void getReturnsJobByCode() {
        KnowledgeExportJob job = job("job-abc", ExportStatus.SUCCEEDED);
        when(jobRepo.findByTenantIdAndJobCode("t-1", "job-abc")).thenReturn(Optional.of(job));

        KnowledgeExportJob loaded = service.get("job-abc");
        assertThat(loaded.status()).isEqualTo(ExportStatus.SUCCEEDED);
    }

    @Test
    void getMissingThrowsNotFound() {
        when(jobRepo.findByTenantIdAndJobCode("t-1", "nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get("nope"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void cancelTerminalJobIsRejected() {
        KnowledgeExportJob done = job("job-x", ExportStatus.SUCCEEDED);
        when(jobRepo.findByTenantIdAndJobCode("t-1", "job-x")).thenReturn(Optional.of(done));

        assertThatThrownBy(() -> service.cancel("job-x"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void cancelPendingJobTransitionsToCancelled() {
        KnowledgeExportJob pending = job("job-p", ExportStatus.PENDING);
        when(jobRepo.findByTenantIdAndJobCode("t-1", "job-p")).thenReturn(Optional.of(pending));

        service.cancel("job-p");

        ArgumentCaptor<KnowledgeExportJob> cap = ArgumentCaptor.forClass(KnowledgeExportJob.class);
        Mockito.verify(jobRepo, Mockito.times(1)).save(cap.capture());
        assertThat(cap.getValue().status()).isEqualTo(ExportStatus.CANCELLED);
    }

    @Test
    void executeJobMarksRunningThenSucceeded() throws Exception {
        KnowledgeExportJob pending = job("job-r", ExportStatus.PENDING);
        when(jobRepo.findByTenantIdAndJobCode("t-1", "job-r")).thenReturn(Optional.of(pending));
        when(identityRepo.countByTenantId("t-1")).thenReturn(42L);
        when(identityRepo.pageByTenantId("t-1", 0, 500)).thenReturn(List.of(
            identity(1L, "DRUG.A"),
            identity(2L, "GUIDE.B")
        ));

        service.executeJob("job-r");

        // 至少两次 save：RUNNING + SUCCEEDED
        ArgumentCaptor<KnowledgeExportJob> cap = ArgumentCaptor.forClass(KnowledgeExportJob.class);
        Mockito.verify(jobRepo, Mockito.atLeast(2)).save(cap.capture());
        KnowledgeExportJob last = cap.getAllValues().get(cap.getAllValues().size() - 1);
        assertThat(last.status()).isEqualTo(ExportStatus.SUCCEEDED);
        assertThat(last.progress()).isEqualTo(100);
        assertThat(last.itemCount()).isEqualTo(2L);
        assertThat(last.resultUri()).isEqualTo("/api/v1/engine/knowledge/exports/job-r/download");
        assertThat(last.expiresAt()).isAfter(Instant.now());

        Path exportPath = service.physicalExportPathForTest("job-r");
        assertThat(Files.exists(exportPath)).isTrue();
        assertThat(Files.readString(exportPath)).contains("\"identityCode\":\"DRUG.A\"");
    }

    @Test
    void lineageExportIncludesInvalidationAndAffectedCaseTaskEvidence() throws Exception {
        KnowledgeExportJob pending = job("job-lineage", ExportType.LINEAGE, ExportStatus.PENDING);
        when(jobRepo.findByTenantIdAndJobCode("t-1", "job-lineage")).thenReturn(Optional.of(pending));
        when(identityRepo.pageByTenantId("t-1", 0, 500)).thenReturn(List.of());
        when(versionRepo.pageByTenantId("t-1", 0, 500)).thenReturn(List.of());
        when(supersessionRepo.pageByTenantId("t-1", 0, 500)).thenReturn(List.of());
        when(invalidationRepo.pageByTenantId("t-1", 0, 500)).thenReturn(List.of(invalidation()));
        when(affectedCaseTaskRepo.pageByTenantId("t-1", 0, 500)).thenReturn(List.of(affectedTask()));

        service.executeJob("job-lineage");

        String exported = Files.readString(service.physicalExportPathForTest("job-lineage"));
        assertThat(exported)
            .contains("\"recordType\":\"knowledge_invalidation\"")
            .contains("\"recordType\":\"affected_case_task\"")
            .contains("INV-77")
            .contains("PHYSICIAN_REVIEW");
    }

    @Test
    void markFailedRecordsErrorMessage() {
        KnowledgeExportJob running = job("job-f", ExportStatus.RUNNING);
        when(jobRepo.findByTenantIdAndJobCode("t-1", "job-f")).thenReturn(Optional.of(running));

        service.markFailed("job-f", "disk full");

        ArgumentCaptor<KnowledgeExportJob> cap = ArgumentCaptor.forClass(KnowledgeExportJob.class);
        Mockito.verify(jobRepo).save(cap.capture());
        assertThat(cap.getValue().status()).isEqualTo(ExportStatus.FAILED);
        assertThat(cap.getValue().errorMessage()).isEqualTo("disk full");
    }

    private KnowledgeExportJob job(String code, ExportStatus status) {
        return job(code, ExportType.IDENTITIES, status);
    }

    private KnowledgeExportJob job(String code, ExportType type, ExportStatus status) {
        Instant now = Instant.now();
        return new KnowledgeExportJob(
            1L, "t-1", code, "u-99", type, null,
            status, 0, null, null, null,
            now, null, null, null
        );
    }

    private KnowledgeInvalidation invalidation() {
        Instant now = Instant.now();
        return new KnowledgeInvalidation(
            77L, "t-1", 1L, 5L,
            KnowledgeInvalidationType.EMERGENCY_WITHDRAW,
            KnowledgeInvalidationStatus.OPEN,
            KnowledgeRiskLevel.HIGH,
            "说明书新增禁忌证",
            "tenant:t-1",
            KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE,
            "u-99",
            now,
            true,
            "trace",
            now,
            "u-99",
            now,
            "u-99"
        );
    }

    private AffectedCaseTask affectedTask() {
        Instant now = Instant.now();
        return new AffectedCaseTask(
            88L, "t-1", "INV-77:PHYSICIAN_REVIEW:version:5", 77L, 1L, 5L,
            AffectedCaseTaskType.PHYSICIAN_REVIEW,
            AffectedCaseTaskStatus.OPEN,
            AffectedCaseTargetType.KNOWLEDGE_VERSION,
            "identity:1/version:5",
            "说明书新增禁忌证",
            now.plusSeconds(86_400),
            "u-99",
            "trace",
            now,
            "u-99",
            now,
            "u-99"
        );
    }

    private KnowledgeIdentity identity(Long id, String code) {
        Instant now = Instant.now();
        return new KnowledgeIdentity(
            id, "t-1", code, KnowledgeDomain.DRUG, "知识主题 " + id, null, null,
            KnowledgeIdentityStatus.ACTIVE, null, now, "u-99", now, "u-99"
        );
    }

    private ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }
}
