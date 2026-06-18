package com.medkernel.engine.knowledge.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.medkernel.shared.config.SystemConfigService;
import com.medkernel.shared.hash.Sha256ContentHash;

@ExtendWith(MockitoExtension.class)
class ManagedDocumentMaterialStorageTest {

    @Mock SystemConfigService configService;
    @Mock KnowledgeMaterialObjectRepository repository;

    @TempDir
    Path tempDir;

    @Test
    void storesBytesUnderConfiguredManagedFileRootAndWritesLedger() throws Exception {
        Path root = tempDir.resolve("platform-knowledge/t-1/literature-materials/2026");
        String rootUri = root.toUri().toString();
        byte[] bytes = "原文".getBytes(StandardCharsets.UTF_8);
        String sha256 = Sha256ContentHash.sha256Bytes(bytes, "文档原件不能为空");
        when(configService.runtimeKnowledgeLiteratureMaterialRootUri()).thenReturn(rootUri);
        when(repository.findByTenantIdAndScopeKeyAndSha256("tenant-1", "tenant-1", sha256))
            .thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> {
            KnowledgeMaterialObject object = invocation.getArgument(0);
            return new KnowledgeMaterialObject(
                12L,
                object.tenantId(),
                object.scopeKey(),
                object.fileUri(),
                object.sha256(),
                object.contentType(),
                object.byteSize(),
                object.storageBackend(),
                object.sourceChannel(),
                object.storedAt(),
                object.storedBy());
        });

        ManagedDocumentMaterialStorage storage = new ManagedDocumentMaterialStorage(configService, repository);
        StoredDocumentMaterial stored = storage.store(new DocumentMaterialStoreRequest(
            "tenant-1",
            "tenant-1",
            bytes,
            "指南.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            sha256,
            "DOC_PARSE",
            "u"));

        assertThat(stored.storageBackend()).isEqualTo("LOCAL_FILE");
        assertThat(stored.fileUri()).startsWith(rootUri);
        assertThat(Files.readAllBytes(Path.of(java.net.URI.create(stored.fileUri())))).isEqualTo(bytes);
    }

    @Test
    void deletesManagedFileAndLedgerTogether() throws Exception {
        Path material = tempDir.resolve("platform-knowledge/t-1/literature-materials/tenant-1/a/doc.txt");
        Files.createDirectories(material.getParent());
        Files.writeString(material, "原文", StandardCharsets.UTF_8);
        KnowledgeMaterialObject object = new KnowledgeMaterialObject(
            12L,
            "tenant-1",
            "tenant-1",
            material.toUri().toString(),
            "a".repeat(64),
            "text/plain; charset=UTF-8",
            Files.size(material),
            "LOCAL_FILE",
            "DOC_PARSE",
            Instant.parse("2026-06-16T00:00:00Z"),
            "u");
        when(repository.findByTenantIdAndFileUri("tenant-1", object.fileUri()))
            .thenReturn(Optional.of(object));

        ManagedDocumentMaterialStorage storage = new ManagedDocumentMaterialStorage(configService, repository);
        storage.delete("tenant-1", object.fileUri());

        assertThat(material).doesNotExist();
        verify(repository).delete(object);
    }
}
