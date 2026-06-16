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
            new ParsedSection("1", 1, "总则", List.of(
                new ParsedParagraph("成人适用。", null), new ParsedParagraph("禁用于孕妇。", null))),
            new ParsedSection("1.1", 2, "适应证", List.of(
                new ParsedParagraph("确诊后使用。", null)))), List.of());
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
    void encodesPagePrefixInAnchorWhenParagraphCarriesPage() {
        when(versionRepository.findBySourceDocumentIdAndContentHash(anyLong(), anyString()))
            .thenReturn(Optional.empty());
        when(versionRepository.save(any())).thenAnswer(i -> {
            SourceVersion v = i.getArgument(0);
            return new SourceVersion(77L, v.tenantId(), v.sourceDocumentId(), v.versionNo(),
                v.publishedAt(), v.contentHash(), v.fileUri(), v.language(), v.createdAt(), v.createdBy());
        });
        when(fragmentRepository.findBySourceVersionIdAndContentHash(anyLong(), anyString()))
            .thenReturn(Optional.empty());
        when(fragmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ParsedDocument paged = new ParsedDocument(List.of(
            new ParsedSection("2", 1, "治疗", List.of(
                new ParsedParagraph("一线用药。", 3), new ParsedParagraph("二线用药。", 4)))), List.of());

        materializer.materialize("t-1", 5L, "v1", "file:/g.pdf", "b".repeat(64), paged, "tester");

        ArgumentCaptor<SourceFragment> cap = ArgumentCaptor.forClass(SourceFragment.class);
        verify(fragmentRepository, times(2)).save(cap.capture());
        assertThat(cap.getAllValues()).extracting(SourceFragment::anchorPath)
            .containsExactly("p3/§2/¶1", "p4/§2/¶2");
    }

    @Test
    void materializesTableCellsWithTableAnchorsSkippingBlankCells() {
        when(versionRepository.findBySourceDocumentIdAndContentHash(anyLong(), anyString()))
            .thenReturn(Optional.empty());
        when(versionRepository.save(any())).thenAnswer(i -> {
            SourceVersion v = i.getArgument(0);
            return new SourceVersion(55L, v.tenantId(), v.sourceDocumentId(), v.versionNo(),
                v.publishedAt(), v.contentHash(), v.fileUri(), v.language(), v.createdAt(), v.createdBy());
        });
        when(fragmentRepository.findBySourceVersionIdAndContentHash(anyLong(), anyString()))
            .thenReturn(Optional.empty());
        when(fragmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // 单节 + 1 张表（2 行 × 2 列，r2c2 空），Word 来源无版式页（page=null）。
        ParsedDocument withTable = new ParsedDocument(
            List.of(new ParsedSection("1", 1, "用法用量", List.of(new ParsedParagraph("口服。", null)))),
            List.of(new ParsedTable("1", "用法用量", 1, null, List.of(
                List.of("药品", "剂量"),
                List.of("阿司匹林", "")))));

        MaterializationResult result = materializer.materialize(
            "t-1", 5L, "v1", "file:/d.docx", "c".repeat(64), withTable, "tester");

        // 1 段落片段 + 3 个非空单元格片段（r2c2 空跳过）。
        assertThat(result.fragmentCount()).isEqualTo(4);
        ArgumentCaptor<SourceFragment> cap = ArgumentCaptor.forClass(SourceFragment.class);
        verify(fragmentRepository, times(4)).save(cap.capture());
        assertThat(cap.getAllValues()).extracting(SourceFragment::anchorPath)
            .containsExactly("§1/¶1", "§1/tbl1/r1c1", "§1/tbl1/r1c2", "§1/tbl1/r2c1");
        assertThat(cap.getAllValues()).filteredOn(f -> f.anchorPath().contains("tbl"))
            .allSatisfy(f -> {
                assertThat(f.anchorLabel()).isEqualTo("用法用量");
                assertThat(f.contentHash()).hasSize(64);
            });
        assertThat(cap.getAllValues().get(2).textExcerpt()).isEqualTo("剂量");
    }

    @Test
    void encodesPagePrefixInTableAnchorForPagedSource() {
        when(versionRepository.findBySourceDocumentIdAndContentHash(anyLong(), anyString()))
            .thenReturn(Optional.empty());
        when(versionRepository.save(any())).thenAnswer(i -> {
            SourceVersion v = i.getArgument(0);
            return new SourceVersion(66L, v.tenantId(), v.sourceDocumentId(), v.versionNo(),
                v.publishedAt(), v.contentHash(), v.fileUri(), v.language(), v.createdAt(), v.createdBy());
        });
        when(fragmentRepository.findBySourceVersionIdAndContentHash(anyLong(), anyString()))
            .thenReturn(Optional.empty());
        when(fragmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // 版式来源（如 PDF）表格携真实页号 → 锚点带 p 前缀，证表格锚点方案两格式通用。
        ParsedDocument pagedTable = new ParsedDocument(
            List.of(),
            List.of(new ParsedTable("2", "剂量表", 1, 2, List.of(List.of("成人", "10mg")))));

        materializer.materialize("t-1", 5L, "v1", "file:/g.pdf", "d".repeat(64), pagedTable, "tester");

        ArgumentCaptor<SourceFragment> cap = ArgumentCaptor.forClass(SourceFragment.class);
        verify(fragmentRepository, times(2)).save(cap.capture());
        assertThat(cap.getAllValues()).extracting(SourceFragment::anchorPath)
            .containsExactly("p2/§2/tbl1/r1c1", "p2/§2/tbl1/r1c2");
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
