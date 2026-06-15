package com.medkernel.engine.knowledge.parsing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

@ExtendWith(MockitoExtension.class)
class DocumentParseOrchestrationServiceTest {

    @Mock DocParseJobRepository jobRepository;
    @Mock SourceDocumentRepository sourceDocumentRepository;
    @Mock ParsedDocumentMaterializer materializer;
    @Mock AuditRecorder auditRecorder;

    private DocumentParseOrchestrationService service;

    @BeforeEach
    void setUp() {
        service = new DocumentParseOrchestrationService(
            jobRepository, sourceDocumentRepository,
            List.of(new StructuredTextDocumentParser()), materializer, auditRecorder);
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
        when(materializer.materialize(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new MaterializationResult(99L, 1, 2));

        DocParseJob job = service.submit(req("# 总则\n成人适用。\n禁用于孕妇。\n", DocumentFormat.STRUCTURED_TEXT));

        assertThat(job.status()).isEqualTo(ParseJobStatus.SUCCEEDED);
        assertThat(job.resultSourceVersionId()).isEqualTo(99L);
        assertThat(job.parsedFragmentCount()).isEqualTo(2);
        assertThat(job.sourceHash()).hasSize(64);
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
}
