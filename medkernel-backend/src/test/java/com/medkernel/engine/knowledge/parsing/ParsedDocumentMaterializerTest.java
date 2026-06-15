package com.medkernel.engine.knowledge.parsing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.medkernel.engine.knowledge.SourceFragment;
import com.medkernel.engine.knowledge.SourceFragmentRepository;
import com.medkernel.engine.knowledge.SourceVersion;
import com.medkernel.engine.knowledge.SourceVersionRepository;

@ExtendWith(MockitoExtension.class)
class ParsedDocumentMaterializerTest {

    @Mock SourceVersionRepository versionRepository;
    @Mock SourceFragmentRepository fragmentRepository;
    @InjectMocks ParsedDocumentMaterializer materializer;

    private ParsedDocument doc() {
        return new ParsedDocument(List.of(
            new ParsedSection("1", 1, "总则", List.of("成人适用。", "禁用于孕妇。")),
            new ParsedSection("1.1", 2, "适应证", List.of("确诊后使用。"))));
    }

    @Test
    void materializesVersionAndFragmentsWithHierarchicalAnchors() {
        when(versionRepository.findBySourceDocumentIdAndContentHash(anyLong(), anyString()))
            .thenReturn(Optional.empty());
        when(versionRepository.save(any())).thenAnswer(i -> {
            SourceVersion v = i.getArgument(0);
            return new SourceVersion(99L, v.tenantId(), v.sourceDocumentId(), v.versionNo(),
                v.publishedAt(), v.contentHash(), v.fileUri(), v.language(), v.createdAt(), v.createdBy());
        });
        when(fragmentRepository.findBySourceVersionIdAndContentHash(anyLong(), anyString()))
            .thenReturn(Optional.empty());
        when(fragmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MaterializationResult result = materializer.materialize(
            "t-1", 5L, "v1", "file:/g.txt", "a".repeat(64), doc(), "tester");

        assertThat(result.sourceVersionId()).isEqualTo(99L);
        assertThat(result.sectionCount()).isEqualTo(2);
        assertThat(result.fragmentCount()).isEqualTo(3);

        ArgumentCaptor<SourceFragment> cap = ArgumentCaptor.forClass(SourceFragment.class);
        verify(fragmentRepository, times(3)).save(cap.capture());
        assertThat(cap.getAllValues()).extracting(SourceFragment::anchorPath)
            .containsExactly("§1/¶1", "§1/¶2", "§1.1/¶1");
        assertThat(cap.getAllValues()).allSatisfy(f -> {
            assertThat(f.tenantId()).isEqualTo("t-1");
            assertThat(f.sourceVersionId()).isEqualTo(99L);
            assertThat(f.contentHash()).hasSize(64);
        });
        assertThat(cap.getAllValues().get(0).anchorLabel()).isEqualTo("总则");
        assertThat(cap.getAllValues().get(0).textExcerpt()).isEqualTo("成人适用。");
    }

    @Test
    void reusesExistingVersionAndSkipsDuplicateFragmentsIdempotently() {
        SourceVersion existing = new SourceVersion(99L, "t-1", 5L, "v1", Instant.now(),
            "a".repeat(64), "file:/g.txt", "zh-CN", Instant.now(), "tester");
        when(versionRepository.findBySourceDocumentIdAndContentHash(5L, "a".repeat(64)))
            .thenReturn(Optional.of(existing));
        when(fragmentRepository.findBySourceVersionIdAndContentHash(anyLong(), anyString()))
            .thenReturn(Optional.of(new SourceFragment(1L, "t-1", 99L, "§1/¶1", "总则", "成人适用。", "x", Instant.now())));

        MaterializationResult result = materializer.materialize(
            "t-1", 5L, "v1", "file:/g.txt", "a".repeat(64), doc(), "tester");

        assertThat(result.sourceVersionId()).isEqualTo(99L);
        assertThat(result.fragmentCount()).isZero();
        verify(versionRepository, never()).save(any());
        verify(fragmentRepository, never()).save(any());
    }
}
