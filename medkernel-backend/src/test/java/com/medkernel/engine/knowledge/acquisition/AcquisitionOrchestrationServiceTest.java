package com.medkernel.engine.knowledge.acquisition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.SourceDocument;
import com.medkernel.engine.knowledge.SourceDocumentRepository;
import com.medkernel.engine.knowledge.SourceType;
import com.medkernel.engine.knowledge.SourceVersion;
import com.medkernel.engine.knowledge.SourceVersionRepository;
import com.medkernel.engine.knowledge.parsing.DocParseJob;
import com.medkernel.engine.knowledge.parsing.DocumentFormat;
import com.medkernel.engine.knowledge.parsing.DocumentParseOrchestrationService;
import com.medkernel.engine.knowledge.parsing.ParseJobStatus;
import com.medkernel.engine.llm.provider.DeploymentForm;
import com.medkernel.engine.llm.provider.DeploymentFormService;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.hash.Sha256ContentHash;

class AcquisitionOrchestrationServiceTest {

    private KnowledgeAcquisitionSourceRepository sourceRepository;
    private KnowledgeAcquisitionRunRepository runRepository;
    private SourceDocumentRepository sourceDocuments;
    private SourceVersionRepository sourceVersions;
    private DocumentParseOrchestrationService parseService;
    private DeploymentFormService deploymentFormService;
    private WebContentFetcher fetcher;
    private AuditRecorder auditRecorder;
    private AcquisitionOrchestrationService service;

    @BeforeEach
    void setUp() {
        sourceRepository = mock(KnowledgeAcquisitionSourceRepository.class);
        runRepository = mock(KnowledgeAcquisitionRunRepository.class);
        sourceDocuments = mock(SourceDocumentRepository.class);
        sourceVersions = mock(SourceVersionRepository.class);
        parseService = mock(DocumentParseOrchestrationService.class);
        deploymentFormService = mock(DeploymentFormService.class);
        fetcher = mock(WebContentFetcher.class);
        auditRecorder = mock(AuditRecorder.class);
        service = new AcquisitionOrchestrationService(
            sourceRepository, runRepository, sourceDocuments, sourceVersions,
            parseService, deploymentFormService, fetcher, auditRecorder);
        when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        RequestContext.restore(new RequestContext.Snapshot("trace-1", OrgScope.tenant("tenant-1"), "user-001"));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private KnowledgeAcquisitionSource source(String domain) {
        return new KnowledgeAcquisitionSource(
            11L,
            "tenant-1",
            "NHC-HTN",
            domain,
            "https://" + domain,
            SourceType.GUIDELINE,
            SourceAuthorityLevel.B_GUIDELINE,
            "国家卫生健康委公开指南",
            "高血压诊疗指南",
            "国家卫生健康委",
            "公开资料许可",
            AcquisitionLicensePolicy.PERMITTED,
            AcquisitionRobotsPolicy.ALLOW_FETCH,
            "Y",
            "super-admin",
            Instant.parse("2026-06-17T00:00:00Z"),
            Instant.EPOCH,
            "super-admin",
            Instant.EPOCH,
            "super-admin");
    }

    private SourceDocument savedDocument() {
        return new SourceDocument(7L, "tenant-1", "NHC-HTN", SourceType.GUIDELINE,
            SourceAuthorityLevel.B_GUIDELINE, "国家卫生健康委公开指南", "高血压诊疗指南",
            "国家卫生健康委", "公开资料许可", "zh-CN", Instant.EPOCH, "user-001", Instant.EPOCH, "user-001");
    }

    @Test
    void runBlocksOutsideProductionCenterBeforeFetching() {
        when(deploymentFormService.currentForm()).thenReturn(DeploymentForm.HOSPITAL_RUNTIME);

        KnowledgeAcquisitionRunResponse response = service.run(new KnowledgeAcquisitionRunRequest(
            "NHC-HTN", "https://guideline.example.org/htn.txt", "v2026", DocumentFormat.STRUCTURED_TEXT));

        assertThat(response.status()).isEqualTo(KnowledgeAcquisitionRunStatus.BLOCKED);
        assertThat(response.failureReason()).contains("PRODUCTION_CENTER");
        verify(fetcher, never()).fetch(any());

        ArgumentCaptor<KnowledgeAcquisitionRun> saved = ArgumentCaptor.forClass(KnowledgeAcquisitionRun.class);
        verify(runRepository).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(KnowledgeAcquisitionRunStatus.BLOCKED);
        assertThat(saved.getValue().failureReason()).contains("PRODUCTION_CENTER");
    }

    @Test
    void runRejectsNullUrlAsBadRequestBeforeFetching() {
        assertThatThrownBy(() -> service.run(new KnowledgeAcquisitionRunRequest(
                "NHC-HTN", null, "v2026", DocumentFormat.STRUCTURED_TEXT)))
            .isInstanceOf(ApiException.class)
            .extracting(error -> ((ApiException) error).errorCode())
            .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(fetcher, never()).fetch(any());
    }

    @Test
    void runRejectsUrlOutsideAllowlistedDomainBeforeFetching() {
        when(deploymentFormService.currentForm()).thenReturn(DeploymentForm.PRODUCTION_CENTER);
        when(sourceRepository.findByTenantIdAndSourceCode("tenant-1", "NHC-HTN"))
            .thenReturn(Optional.of(source("guideline.example.org")));

        KnowledgeAcquisitionRunResponse response = service.run(new KnowledgeAcquisitionRunRequest(
            "NHC-HTN", "https://evil.example.net/htn.txt", "v2026", DocumentFormat.STRUCTURED_TEXT));

        assertThat(response.status()).isEqualTo(KnowledgeAcquisitionRunStatus.BLOCKED);
        assertThat(response.failureReason()).contains("白名单");
        verify(fetcher, never()).fetch(any());
    }

    @Test
    void runRejectsMalformedSourceDomainBeforeFetching() {
        when(deploymentFormService.currentForm()).thenReturn(DeploymentForm.PRODUCTION_CENTER);
        when(sourceRepository.findByTenantIdAndSourceCode("tenant-1", "NHC-HTN"))
            .thenReturn(Optional.of(source("https://%")));

        KnowledgeAcquisitionRunResponse response = service.run(new KnowledgeAcquisitionRunRequest(
            "NHC-HTN", "https://guideline.example.org/htn.txt", "v2026", DocumentFormat.STRUCTURED_TEXT));

        assertThat(response.status()).isEqualTo(KnowledgeAcquisitionRunStatus.BLOCKED);
        assertThat(response.failureReason()).contains("白名单");
        verify(fetcher, never()).fetch(any());
    }

    @Test
    void runRejectsCrossDomainRedirectBeforeParsing() {
        byte[] body = "# 外域资料".getBytes(StandardCharsets.UTF_8);
        String sourceHash = Sha256ContentHash.sha256Bytes(body, "原文不能为空");
        when(deploymentFormService.currentForm()).thenReturn(DeploymentForm.PRODUCTION_CENTER);
        when(sourceRepository.findByTenantIdAndSourceCode("tenant-1", "NHC-HTN"))
            .thenReturn(Optional.of(source("guideline.example.org")));
        when(fetcher.fetch(URI.create("https://guideline.example.org/redirect")))
            .thenReturn(new FetchedWebContent(URI.create("https://evil.example.net/htn.txt"),
                "text/plain; charset=UTF-8", body, Instant.parse("2026-06-17T08:10:00Z")));
        when(sourceDocuments.findByTenantIdAndSourceCode("tenant-1", "NHC-HTN")).thenReturn(Optional.empty());
        when(sourceDocuments.save(any())).thenReturn(savedDocument());
        when(sourceVersions.findBySourceDocumentIdAndContentHash(7L, sourceHash)).thenReturn(Optional.empty());
        when(parseService.submit(any())).thenReturn(new DocParseJob(
            3L, "tenant-1", "dpj:redirect", 7L, "NHC-HTN-v2026.txt", DocumentFormat.STRUCTURED_TEXT,
            sourceHash, ParseJobStatus.SUCCEEDED, 9L, 1, 1, null,
            Instant.EPOCH, "user-001", Instant.EPOCH, "user-001"));
        when(sourceVersions.findByTenantIdAndId("tenant-1", 9L)).thenReturn(Optional.of(new SourceVersion(
            9L, "tenant-1", 7L, "v2026", Instant.EPOCH, sourceHash,
            "file:///zoesoft/medkernel/platform-knowledge/t-1/literature-materials/tenant-1/NHC-HTN-v2026.txt",
            "zh-CN", Instant.EPOCH, "user-001")));

        KnowledgeAcquisitionRunResponse response = service.run(new KnowledgeAcquisitionRunRequest(
            "NHC-HTN", "https://guideline.example.org/redirect", "v2026", DocumentFormat.STRUCTURED_TEXT));

        assertThat(response.status()).isEqualTo(KnowledgeAcquisitionRunStatus.BLOCKED);
        assertThat(response.failureReason()).contains("重定向域名不在来源白名单");
        verify(parseService, never()).submit(any());
    }

    @Test
    void runFetchesAllowlistedPublicMaterialThroughParsePipelineAndRecordsEvidence() {
        byte[] body = "# 高血压指南\n\n1. 诊断标准\n收缩压持续升高。".getBytes(StandardCharsets.UTF_8);
        String sourceHash = Sha256ContentHash.sha256Bytes(body, "原文不能为空");
        when(deploymentFormService.currentForm()).thenReturn(DeploymentForm.PRODUCTION_CENTER);
        when(sourceRepository.findByTenantIdAndSourceCode("tenant-1", "NHC-HTN"))
            .thenReturn(Optional.of(source("guideline.example.org")));
        when(sourceDocuments.findByTenantIdAndSourceCode("tenant-1", "NHC-HTN")).thenReturn(Optional.empty());
        when(sourceDocuments.save(any())).thenReturn(savedDocument());
        when(sourceVersions.findBySourceDocumentIdAndContentHash(7L, sourceHash)).thenReturn(Optional.empty());
        when(fetcher.fetch(URI.create("https://guideline.example.org/htn.txt")))
            .thenReturn(new FetchedWebContent(URI.create("https://guideline.example.org/htn.txt"),
                "text/plain; charset=UTF-8", body, Instant.parse("2026-06-17T08:00:00Z")));
        when(parseService.submit(any())).thenReturn(new DocParseJob(
            3L, "tenant-1", "dpj:acq", 7L, "NHC-HTN-v2026.txt", DocumentFormat.STRUCTURED_TEXT,
            sourceHash, ParseJobStatus.SUCCEEDED, 9L, 1, 1, null,
            Instant.EPOCH, "user-001", Instant.EPOCH, "user-001"));
        when(sourceVersions.findByTenantIdAndId("tenant-1", 9L)).thenReturn(Optional.of(new SourceVersion(
            9L, "tenant-1", 7L, "v2026", Instant.EPOCH, sourceHash,
            "file:///zoesoft/medkernel/platform-knowledge/t-1/literature-materials/tenant-1/NHC-HTN-v2026.txt",
            "zh-CN", Instant.EPOCH, "user-001")));

        KnowledgeAcquisitionRunResponse response = service.run(new KnowledgeAcquisitionRunRequest(
            "NHC-HTN", "https://guideline.example.org/htn.txt", "v2026", DocumentFormat.STRUCTURED_TEXT));

        assertThat(response.status()).isEqualTo(KnowledgeAcquisitionRunStatus.SUCCEEDED);
        assertThat(response.sourceHash()).isEqualTo(sourceHash);
        assertThat(response.sourceVersionId()).isEqualTo(9L);
        assertThat(response.materialFileUri()).startsWith("file://");

        ArgumentCaptor<KnowledgeAcquisitionRun> saved = ArgumentCaptor.forClass(KnowledgeAcquisitionRun.class);
        verify(runRepository).save(saved.capture());
        KnowledgeAcquisitionRun run = saved.getValue();
        assertThat(run.url()).isEqualTo("https://guideline.example.org/htn.txt");
        assertThat(run.domain()).isEqualTo("guideline.example.org");
        assertThat(run.byteSize()).isEqualTo((long) body.length);
        assertThat(run.contentType()).isEqualTo("text/plain; charset=UTF-8");
        assertThat(run.licensePolicy()).isEqualTo(AcquisitionLicensePolicy.PERMITTED);
        assertThat(run.robotsPolicy()).isEqualTo(AcquisitionRobotsPolicy.ALLOW_FETCH);
        assertThat(run.materialFileUri()).startsWith("file://");
        assertThat(run.parseJobCode()).isEqualTo("dpj:acq");
        verify(auditRecorder).record(AuditAction.EXECUTE, "mk_knowledge_acquisition_run",
            run.runCode(), "公域资料获取成功：NHC-HTN");
    }
}
