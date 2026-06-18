package com.medkernel.engine.knowledge.parsing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.SourceDocument;
import com.medkernel.engine.knowledge.SourceDocumentRepository;
import com.medkernel.engine.knowledge.SourceType;
import com.medkernel.engine.knowledge.SourceVersion;
import com.medkernel.engine.knowledge.SourceVersionRepository;
import com.medkernel.engine.knowledge.material.DocumentMaterialStoragePort;
import com.medkernel.engine.knowledge.material.StoredDocumentMaterial;
import com.medkernel.engine.knowledge.production.KnowledgeDomain;
import com.medkernel.engine.knowledge.production.MaterializationTarget;
import com.medkernel.engine.knowledge.production.NewIdentitySpec;
import com.medkernel.engine.knowledge.production.TargetPipeline;
import com.medkernel.engine.knowledge.production.generation.CandidateGenerationOrchestrationService;
import com.medkernel.engine.knowledge.production.generation.CandidateGenerationRequest;
import com.medkernel.engine.knowledge.production.generation.GenerationItem;
import com.medkernel.engine.knowledge.production.generation.GenerationSummary;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.hash.Sha256ContentHash;

@ExtendWith(MockitoExtension.class)
class DocumentParseOrchestrationServiceTest {

    @Mock DocParseJobRepository jobRepository;
    @Mock SourceDocumentRepository sourceDocumentRepository;
    @Mock SourceVersionRepository sourceVersionRepository;
    @Mock ParsedDocumentMaterializer materializer;
    @Mock DocumentMaterialStoragePort materialStorage;
    @Mock CandidateGenerationOrchestrationService candidateGeneration;
    @Mock AuditRecorder auditRecorder;

    private DocumentParseOrchestrationService service;

    @BeforeEach
    void setUp() {
        service = new DocumentParseOrchestrationService(
            jobRepository, sourceDocumentRepository, sourceVersionRepository,
            List.of(new StructuredTextDocumentParser()), materializer, materialStorage,
            candidateGeneration, auditRecorder);
        RequestContext.restore(new RequestContext.Snapshot("trace-1", OrgScope.tenant("tenant-1"), "user-001"));
        org.mockito.Mockito.lenient().when(jobRepository.save(any())).thenAnswer(i -> {
            DocParseJob j = i.getArgument(0);
            return j.id() == null
                ? new DocParseJob(1L, j.tenantId(), j.jobCode(), j.sourceDocumentId(), j.sourceFileName(),
                    j.documentFormat(), j.sourceHash(), j.status(), j.resultSourceVersionId(),
                    j.parsedSectionCount(), j.parsedFragmentCount(), j.errorMessage(),
                    j.createdAt(), j.createdBy(), j.updatedAt(), j.updatedBy())
                : j;
        });
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private DocumentParseRequest req(String content, DocumentFormat fmt) {
        return new DocumentParseRequest(5L, "v1", "g.txt", fmt, content);
    }

    private SourceDocument stubDoc() {
        return new SourceDocument(5L, "tenant-1", "SRC-1", SourceType.GUIDELINE,
            SourceAuthorityLevel.B_GUIDELINE, "国家卫健委发布", "高血压指南",
            "卫健委", null, "zh-CN", Instant.now(), "admin", Instant.now(), "admin");
    }

    @Test
    void parsesAndMaterializesToSucceeded() {
        when(sourceDocumentRepository.findByTenantIdAndId("tenant-1", 5L))
            .thenReturn(Optional.of(stubDoc()));
        when(materialStorage.store(any())).thenReturn(new StoredDocumentMaterial(
            12L,
            "tenant-1",
            "tenant-1",
            "file:///zoesoft/medkernel/platform-knowledge/t-1/literature-materials/tenant-1/"
                + "0d/0d0d/g.txt",
            "a".repeat(64),
            "text/plain; charset=UTF-8",
            42L,
            "LOCAL_FILE"));
        when(materializer.materialize(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new MaterializationResult(99L, 1, 2));

        DocParseJob job = service.submit(req("# 总则\n成人适用。\n禁用于孕妇。\n", DocumentFormat.STRUCTURED_TEXT));

        assertThat(job.status()).isEqualTo(ParseJobStatus.SUCCEEDED);
        assertThat(job.resultSourceVersionId()).isEqualTo(99L);
        assertThat(job.parsedFragmentCount()).isEqualTo(2);
        assertThat(job.sourceHash()).hasSize(64);
        verify(materializer).materialize(
            any(), any(), any(),
            org.mockito.ArgumentMatchers.startsWith("file:///zoesoft/medkernel/platform-knowledge"),
            any(), any(), any());
        verify(materialStorage).store(org.mockito.ArgumentMatchers.argThat(request ->
            request.scopeKey().equals("tenant-1")
                && request.fileName().equals("g.txt")
                && request.sourceChannel().equals("DOC_PARSE")
                && request.bytes().length > 0));
    }

    @Test
    void tenantUploadParsesThroughManagedStorageThenGeneratesTenantOverlayCandidate() {
        when(sourceDocumentRepository.findByTenantIdAndId("tenant-1", 5L))
            .thenReturn(Optional.of(stubDoc()));
        when(materialStorage.store(any())).thenReturn(new StoredDocumentMaterial(
            12L,
            "tenant-1",
            "tenant-1",
            "file:///zoesoft/medkernel/platform-knowledge/t-1/literature-materials/tenant-1/"
                + "0d/0d0d/g.txt",
            "a".repeat(64),
            "text/plain; charset=UTF-8",
            42L,
            "LOCAL_FILE"));
        when(materializer.materialize(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new MaterializationResult(99L, 1, 2));
        GenerationSummary generationSummary = new GenerationSummary(List.of(), List.of(), List.of());
        when(candidateGeneration.generate(any())).thenReturn(generationSummary);

        DocumentParseResponse response = service.submitTenantUpload(
            req("# 总则\n成人适用。\n", DocumentFormat.STRUCTURED_TEXT),
            new DocumentUploadGenerationRequest(KnowledgeDomain.CLINICAL, List.of(new GenerationItem(
                VersionedAssetType.RULE,
                new MaterializationTarget(null, new NewIdentitySpec(
                    com.medkernel.engine.knowledge.KnowledgeDomain.GUIDELINE,
                    "院内高血压规则",
                    "LOCAL-HTN-RULE"))))));

        assertThat(response.parseJob().status()).isEqualTo(ParseJobStatus.SUCCEEDED);
        assertThat(response.generationSummary()).isSameAs(generationSummary);
        ArgumentCaptor<CandidateGenerationRequest> generation =
            ArgumentCaptor.forClass(CandidateGenerationRequest.class);
        verify(candidateGeneration).generate(generation.capture());
        assertThat(generation.getValue().sourceVersionId()).isEqualTo(99L);
        assertThat(generation.getValue().targetPipeline()).isEqualTo(TargetPipeline.TENANT_OVERLAY);
        verify(materialStorage).store(org.mockito.ArgumentMatchers.argThat(request ->
            request.scopeKey().equals("tenant-1")
                && request.tenantId().equals("tenant-1")
                && request.sourceChannel().equals("DOC_PARSE")));
    }

    @Test
    void reparsesSucceededJobFromManagedMaterialWithoutRestoringFromUpload() {
        byte[] bytes = "# 总则\n成人适用。\n禁用于孕妇。\n".getBytes(StandardCharsets.UTF_8);
        String hash = Sha256ContentHash.sha256Bytes(bytes, "文档原件不能为空");
        DocParseJob original = new DocParseJob(1L, "tenant-1", "dpj:old", 5L, "g.txt",
            DocumentFormat.STRUCTURED_TEXT, hash, ParseJobStatus.SUCCEEDED, 99L, 1, 2, null,
            Instant.parse("2026-06-16T00:00:00Z"), "u",
            Instant.parse("2026-06-16T00:00:00Z"), "u");
        SourceVersion version = new SourceVersion(99L, "tenant-1", 5L, "v1",
            Instant.parse("2026-06-16T00:00:00Z"), hash,
            "file:///zoesoft/medkernel/platform-knowledge/t-1/literature-materials/tenant-1/0d/g.txt",
            "zh-CN", Instant.parse("2026-06-16T00:00:00Z"), "u");
        when(jobRepository.findByTenantIdAndJobCode("tenant-1", "dpj:old")).thenReturn(Optional.of(original));
        when(sourceVersionRepository.findByTenantIdAndId("tenant-1", 99L)).thenReturn(Optional.of(version));
        when(materialStorage.fetch("tenant-1", version.fileUri())).thenReturn(bytes);
        when(materializer.materialize(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new MaterializationResult(99L, 1, 0));

        DocParseJob reparsed = service.reparse("dpj:old");

        assertThat(reparsed.status()).isEqualTo(ParseJobStatus.SUCCEEDED);
        assertThat(reparsed.sourceHash()).isEqualTo(hash);
        assertThat(reparsed.resultSourceVersionId()).isEqualTo(99L);
        verify(materialStorage).fetch("tenant-1", version.fileUri());
        verify(materialStorage, never()).store(any());
        verify(materializer).materialize(
            org.mockito.ArgumentMatchers.eq("tenant-1"),
            org.mockito.ArgumentMatchers.eq(5L),
            org.mockito.ArgumentMatchers.eq("v1"),
            org.mockito.ArgumentMatchers.eq(version.fileUri()),
            org.mockito.ArgumentMatchers.eq(hash),
            any(),
            org.mockito.ArgumentMatchers.eq("user-001"));
    }

    @Test
    void unparseableContentFailsHonestlyWithoutFakeStructure() {
        when(sourceDocumentRepository.findByTenantIdAndId("tenant-1", 5L))
            .thenReturn(Optional.of(stubDoc()));

        DocParseJob job = service.submit(req("   \n  \n", DocumentFormat.STRUCTURED_TEXT));

        assertThat(job.status()).isEqualTo(ParseJobStatus.FAILED);
        assertThat(job.errorMessage()).contains("空文档");
        assertThat(job.resultSourceVersionId()).isNull();
        verify(materializer, never()).materialize(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void unsupportedFormatFailsHonestly() {
        when(sourceDocumentRepository.findByTenantIdAndId("tenant-1", 5L))
            .thenReturn(Optional.of(stubDoc()));
        // WORD 适配器尚未接入（PR3）；合法 Base64 证明已解码并分派，仅诚实判「暂不支持」
        String wordBase64 = java.util.Base64.getEncoder()
            .encodeToString("DOCX-BYTES".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        DocParseJob job = service.submit(req(wordBase64, DocumentFormat.WORD));

        assertThat(job.status()).isEqualTo(ParseJobStatus.FAILED);
        assertThat(job.errorMessage()).contains("暂不支持");
        verify(materializer, never()).materialize(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsInvalidBase64ForBinaryFormat() {
        assertThatThrownBy(() -> service.submit(req("not*valid*base64*", DocumentFormat.PDF)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Base64");
        verify(materializer, never()).materialize(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void unknownSourceDocumentRejected() {
        when(sourceDocumentRepository.findByTenantIdAndId("tenant-1", 5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.submit(req("# x\ny\n", DocumentFormat.STRUCTURED_TEXT)))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void listJobsReturnsTenantScopedPageWithTotal() {
        DocParseJob row = parseJob("dpj-page-2");
        when(jobRepository.countByTenantId("tenant-1")).thenReturn(41L);
        when(jobRepository.pageByTenantId("tenant-1", 20, 20)).thenReturn(List.of(row));

        PageResponse<DocParseJob> page = service.listJobs(2, 20);

        assertThat(page.items()).containsExactly(row);
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.total()).isEqualTo(41L);
        assertThat(page.hasNext()).isTrue();
        verify(jobRepository).countByTenantId("tenant-1");
        verify(jobRepository).pageByTenantId("tenant-1", 20, 20);
    }

    private static DocParseJob parseJob(String jobCode) {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        return new DocParseJob(1L, "tenant-1", jobCode, 5L, "g.txt", DocumentFormat.STRUCTURED_TEXT,
            "a".repeat(64), ParseJobStatus.SUCCEEDED, 99L, 1, 1, null,
            now, "u", now, "u");
    }
}
