package com.medkernel.engine.knowledge.production.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.engine.factory.AssetSourceRef;
import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.ResolvedSource;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.SourceDocument;
import com.medkernel.engine.knowledge.SourceDocumentRepository;
import com.medkernel.engine.knowledge.SourceReferenceResolver;
import com.medkernel.engine.knowledge.SourceType;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.hash.Sha256ContentHash;

class SourceLicenseGateTest {

    private static final String PAYLOAD = "{\"template\":\"RULE\",\"sections\":{}}";

    private SourceReferenceResolver resolver;
    private SourceDocumentRepository documents;
    private SourceLicenseGate gate;

    @BeforeEach
    void setUp() {
        resolver = mock(SourceReferenceResolver.class);
        documents = mock(SourceDocumentRepository.class);
        gate = new SourceLicenseGate(resolver, documents);
    }

    private KnowledgeAssetEnvelope envelope(String sourceRef) {
        return new KnowledgeAssetEnvelope(VersionedAssetType.RULE, "identity:1", "主题", "v1",
            List.of(new AssetSourceRef(sourceRef, SourceAuthorityLevel.B_GUIDELINE)),
            SourceAuthorityLevel.B_GUIDELINE, null, null, KnowledgeRiskLevel.MEDIUM, "t-1",
            Sha256ContentHash.sha256(PAYLOAD, "x"), PAYLOAD, AssetVersionStatus.DRAFT);
    }

    private SourceDocument source(String license) {
        return new SourceDocument(7L, "t-1", "SRC", SourceType.GUIDELINE, SourceAuthorityLevel.B_GUIDELINE,
            "依据", "标题", "出版者", license, "zh", Instant.EPOCH, "u", Instant.EPOCH, "u");
    }

    @Test
    void passesWhenSourceRefResolvesAndLicensePresent() {
        when(resolver.resolve("t-1", "SRC:v1:a")).thenReturn(new ResolvedSource(7L, 9L, "a"));
        when(documents.findByTenantIdAndId("t-1", 7L)).thenReturn(Optional.of(source("公开许可")));

        GateItemResult result = gate.evaluate(envelope("SRC:v1:a"), new GateContext("t-1", "job-1"));

        assertThat(result.passed()).isTrue();
    }

    @Test
    void failsWhenSourceLicenseMissing() {
        when(resolver.resolve("t-1", "SRC:v1:a")).thenReturn(new ResolvedSource(7L, 9L, "a"));
        when(documents.findByTenantIdAndId("t-1", 7L)).thenReturn(Optional.of(source(" ")));

        GateItemResult result = gate.evaluate(envelope("SRC:v1:a"), new GateContext("t-1", "job-1"));

        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).contains("许可");
    }

    @Test
    void failsWhenSourceRefCannotResolve() {
        when(resolver.resolve("t-1", "SRC:v9:a")).thenThrow(new RuntimeException("受控来源版本不存在"));

        GateItemResult result = gate.evaluate(envelope("SRC:v9:a"), new GateContext("t-1", "job-1"));

        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).contains("受控来源版本不存在");
    }
}
