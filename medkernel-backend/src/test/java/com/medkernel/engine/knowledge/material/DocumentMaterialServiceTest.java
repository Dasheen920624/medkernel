package com.medkernel.engine.knowledge.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

@ExtendWith(MockitoExtension.class)
class DocumentMaterialServiceTest {

    @Mock KnowledgeMaterialObjectRepository repository;
    @Mock DocumentMaterialStoragePort storage;
    @Mock AuditRecorder auditRecorder;

    private DocumentMaterialService service;

    @BeforeEach
    void setUp() {
        service = new DocumentMaterialService(repository, storage, auditRecorder);
        RequestContext.restore(new RequestContext.Snapshot("trace-material", OrgScope.tenant("tenant-1"), "u"));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void readsTenantScopedMaterialAndRecordsAudit() {
        KnowledgeMaterialObject object = new KnowledgeMaterialObject(
            12L,
            "tenant-1",
            "tenant-1",
            "file:///zoesoft/medkernel/platform-knowledge/t-1/literature-materials/tenant-1/a/doc.txt",
            "a".repeat(64),
            "text/plain; charset=UTF-8",
            6L,
            "LOCAL_FILE",
            "DOC_PARSE",
            Instant.parse("2026-06-16T00:00:00Z"),
            "u");
        when(repository.findByTenantIdAndId("tenant-1", 12L)).thenReturn(Optional.of(object));
        when(storage.fetch("tenant-1", object.fileUri())).thenReturn("原文".getBytes(StandardCharsets.UTF_8));

        DocumentMaterialResponse response = service.getMaterial(12L);

        assertThat(response.id()).isEqualTo(12L);
        assertThat(response.contentBase64()).isEqualTo("5Y6f5paH");
        verify(auditRecorder).record(AuditAction.EXPORT, "mk_knowledge_material_object", "12",
            "读取文档原件资料库对象：" + object.fileUri());
    }
}
