package com.medkernel.engine.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.shared.api.error.ApiException;

/**
 * 受控源引用解析器单元测试（AIK-STD-13，B0 解析/诚实拒收）。
 */
class SourceReferenceResolverTest {

    private SourceDocumentRepository documents;
    private SourceVersionRepository versions;
    private SourceReferenceResolver resolver;

    @BeforeEach
    void setUp() {
        documents = mock(SourceDocumentRepository.class);
        versions = mock(SourceVersionRepository.class);
        resolver = new SourceReferenceResolver(documents, versions);
    }

    private SourceDocument doc() {
        return new SourceDocument(7L, "t1", "SRC-1", SourceType.GUIDELINE, SourceAuthorityLevel.A_REGULATION,
            "依据", "标题", "出版者", "license", "zh", Instant.now(), "u", Instant.now(), "u");
    }

    private SourceVersion ver() {
        return new SourceVersion(9L, "t1", 7L, "v1", Instant.now(), "hash", "uri", "zh", Instant.now(), "u");
    }

    @Test
    void resolvesSourceRefToForeignKeys() {
        when(documents.findByTenantIdAndSourceCode("t1", "SRC-1")).thenReturn(Optional.of(doc()));
        when(versions.findBySourceDocumentIdAndVersionNo(7L, "v1")).thenReturn(Optional.of(ver()));

        ResolvedSource resolved = resolver.resolve("t1", "SRC-1:v1:root/0");

        assertThat(resolved.sourceDocumentId()).isEqualTo(7L);
        assertThat(resolved.sourceVersionId()).isEqualTo(9L);
        assertThat(resolved.anchorPath()).isEqualTo("root/0");
    }

    @Test
    void rejectsWhenDocumentMissing() {
        when(documents.findByTenantIdAndSourceCode("t1", "SRC-X")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> resolver.resolve("t1", "SRC-X:v1:a")).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsWhenVersionMissing() {
        when(documents.findByTenantIdAndSourceCode("t1", "SRC-1")).thenReturn(Optional.of(doc()));
        when(versions.findBySourceDocumentIdAndVersionNo(7L, "v9")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> resolver.resolve("t1", "SRC-1:v9:a")).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsMalformedRef() {
        assertThatThrownBy(() -> resolver.resolve("t1", "bad-ref")).isInstanceOf(ApiException.class);
    }
}
