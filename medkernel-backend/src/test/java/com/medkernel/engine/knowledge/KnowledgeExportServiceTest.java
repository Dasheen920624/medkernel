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
    private KnowledgeExportService service;

    @BeforeEach
    void setUp() {
        jobRepo = Mockito.mock(KnowledgeExportJobRepository.class);
        identityRepo = Mockito.mock(KnowledgeIdentityRepository.class);
        versionRepo = Mockito.mock(KnowledgeAssetVersionRepository.class);
        supersessionRepo = Mockito.mock(KnowledgeSupersessionRepository.class);
        citationRepo = Mockito.mock(CitationRepository.class);
        service = new KnowledgeExportService(jobRepo, identityRepo, versionRepo,
            supersessionRepo, citationRepo, objectMapper(), command -> { });
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
            supersessionRepo, citationRepo, objectMapper(), dispatched::add);

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
        Instant now = Instant.now();
        return new KnowledgeExportJob(
            1L, "t-1", code, "u-99", ExportType.IDENTITIES, null,
            status, 0, null, null, null,
            now, null, null, null
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
