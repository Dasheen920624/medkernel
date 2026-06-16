package com.medkernel.engine.knowledge.parsing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.SourceDocument;
import com.medkernel.engine.knowledge.SourceDocumentRepository;
import com.medkernel.engine.knowledge.SourceFragment;
import com.medkernel.engine.knowledge.SourceFragmentRepository;
import com.medkernel.engine.knowledge.SourceType;
import com.medkernel.engine.knowledge.SourceVersionRepository;
import com.medkernel.shared.config.SystemConfigService;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 文档解析管线端到端集成测试（AIK-STD-02 PR1，真实 H2）。
 *
 * <p>验证：提交结构化文本 → 编排服务确定性解析章节 → 物化进 {@code source_version} + {@code source_fragment}
 * 带真实层级锚点（§章节/¶段）与真实 SHA-256；job 记 SUCCEEDED；再次提交同内容幂等（复用版本、不重复片段）。
 */
@SpringBootTest
@ActiveProfiles("dev")
class DocumentParseIntegrationTest {

    private static final String TENANT = "tenant-parse-it";

    @Autowired
    private DocumentParseOrchestrationService service;
    @Autowired
    private SourceDocumentRepository sourceDocuments;
    @Autowired
    private SourceFragmentRepository sourceFragments;
    @Autowired
    private SourceVersionRepository sourceVersions;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TempDir
    Path materialRoot;

    @BeforeEach
    void configureManagedMaterialRoot() throws IOException {
        Path root = materialRoot.resolve("platform-knowledge/t-1/literature-materials/2026");
        Files.createDirectories(root);
        jdbcTemplate.update("""
            UPDATE mk_config_item
               SET config_value = ?
             WHERE tenant_id = 'SYSTEM' AND config_key = ?
            """, root.toUri().toString(), SystemConfigService.KNOWLEDGE_LITERATURE_MATERIAL_ROOT_URI_KEY);
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private static final String CONTENT = """
        # 总则
        本指南适用于成人高血压患者。

        ## 适应证
        用于确诊的原发性高血压。
        """;

    @Test
    void parsesStructuredTextAndMaterializesAnchoredFragments() {
        RequestContext.restore(new RequestContext.Snapshot("trace-it", OrgScope.tenant(TENANT), "user-it"));
        Instant now = Instant.now();
        SourceDocument doc = sourceDocuments.save(new SourceDocument(null, TENANT, "SRC-PARSE-IT",
            SourceType.GUIDELINE, SourceAuthorityLevel.B_GUIDELINE, "国家卫健委发布", "高血压指南",
            "卫健委", "lic", "zh-CN", now, "u", now, "u"));

        DocParseJob job = service.submit(new DocumentParseRequest(
            doc.id(), "v1", "hypertension.md", DocumentFormat.STRUCTURED_TEXT, CONTENT));

        assertThat(job.status()).isEqualTo(ParseJobStatus.SUCCEEDED);
        assertThat(job.resultSourceVersionId()).isNotNull();
        assertThat(job.sourceHash()).hasSize(64);
        assertThat(job.parsedSectionCount()).isEqualTo(2);
        assertThat(job.parsedFragmentCount()).isEqualTo(2);
        String fileUri = sourceVersions.findByTenantIdAndId(TENANT, job.resultSourceVersionId())
            .orElseThrow()
            .fileUri();
        assertThat(fileUri).startsWith("file:").doesNotStartWith("doc-parse:");

        List<SourceFragment> fragments = sourceFragments
            .findByTenantIdAndSourceVersionIdOrderByAnchorPathAsc(TENANT, job.resultSourceVersionId());
        assertThat(fragments).extracting(SourceFragment::anchorPath)
            .containsExactly("§1.1/¶1", "§1/¶1");
        assertThat(fragments).allSatisfy(f -> assertThat(f.contentHash()).hasSize(64));
        assertThat(fragments).anySatisfy(f ->
            assertThat(f.textExcerpt()).isEqualTo("本指南适用于成人高血压患者。"));
    }

    @Test
    void parsesPdfViaBase64AndMaterializesPageAnchoredFragments() throws IOException {
        RequestContext.restore(new RequestContext.Snapshot("trace-pdf", OrgScope.tenant(TENANT), "user-it"));
        Instant now = Instant.now();
        SourceDocument doc = sourceDocuments.save(new SourceDocument(null, TENANT, "SRC-PARSE-PDF",
            SourceType.GUIDELINE, SourceAuthorityLevel.B_GUIDELINE, "国家卫健委发布", "高血压指南(PDF)",
            "卫健委", "lic", "zh-CN", now, "u", now, "u"));

        String base64 = Base64.getEncoder().encodeToString(twoPageGuidelinePdf());

        DocParseJob job = service.submit(new DocumentParseRequest(
            doc.id(), "v1", "guide.pdf", DocumentFormat.PDF, base64));

        assertThat(job.status()).isEqualTo(ParseJobStatus.SUCCEEDED);
        assertThat(job.sourceHash()).hasSize(64);
        assertThat(job.parsedFragmentCount()).isGreaterThanOrEqualTo(2);

        List<SourceFragment> fragments = sourceFragments
            .findByTenantIdAndSourceVersionIdOrderByAnchorPathAsc(TENANT, job.resultSourceVersionId());
        assertThat(fragments).extracting(SourceFragment::anchorPath)
            .anySatisfy(a -> assertThat(a).startsWith("p1/§"))
            .anySatisfy(a -> assertThat(a).startsWith("p2/§"));
        assertThat(fragments).allSatisfy(f -> assertThat(f.contentHash()).hasSize(64));
    }

    /** 由 PDFBox 确定性构造的 2 页夹具 PDF（ASCII 编号标题 + 段落，避免 CJK 字体内嵌）。 */
    private byte[] twoPageGuidelinePdf() throws IOException {
        List<List<String>> pages = List.of(
            List.of("1 Scope", "This guideline applies to adult patients.",
                "1.1 Population", "Confirmed primary hypertension."),
            List.of("2 Treatment", "First line therapy is drug A."));
        try (PDDocument pdf = new PDDocument()) {
            for (List<String> lines : pages) {
                PDPage page = new PDPage(PDRectangle.A4);
                pdf.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(pdf, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.setLeading(18f);
                    cs.newLineAtOffset(60, 760);
                    boolean first = true;
                    for (String line : lines) {
                        if (!first) {
                            cs.newLine();
                        }
                        cs.showText(line);
                        first = false;
                    }
                    cs.endText();
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            pdf.save(baos);
            return baos.toByteArray();
        }
    }

    @Test
    void parsesWordViaBase64AndMaterializesParagraphAndTableCellFragments() throws IOException {
        RequestContext.restore(new RequestContext.Snapshot("trace-word", OrgScope.tenant(TENANT), "user-it"));
        Instant now = Instant.now();
        SourceDocument doc = sourceDocuments.save(new SourceDocument(null, TENANT, "SRC-PARSE-WORD",
            SourceType.GUIDELINE, SourceAuthorityLevel.B_GUIDELINE, "国家卫健委发布", "高血压指南(Word)",
            "卫健委", "lic", "zh-CN", now, "u", now, "u"));

        String base64 = Base64.getEncoder().encodeToString(guidelineDocxWithTable());

        DocParseJob job = service.submit(new DocumentParseRequest(
            doc.id(), "v1", "guide.docx", DocumentFormat.WORD, base64));

        assertThat(job.status()).isEqualTo(ParseJobStatus.SUCCEEDED);
        assertThat(job.sourceHash()).hasSize(64);
        // 1 段正文片段 + 4 个非空表格单元格片段。
        assertThat(job.parsedFragmentCount()).isEqualTo(5);

        List<SourceFragment> fragments = sourceFragments
            .findByTenantIdAndSourceVersionIdOrderByAnchorPathAsc(TENANT, job.resultSourceVersionId());
        assertThat(fragments).extracting(SourceFragment::anchorPath)
            .contains("§1/¶1", "§1/tbl1/r1c1", "§1/tbl1/r1c2", "§1/tbl1/r2c1", "§1/tbl1/r2c2");
        assertThat(fragments).allSatisfy(f -> assertThat(f.contentHash()).hasSize(64));
        assertThat(fragments).anySatisfy(f -> {
            assertThat(f.anchorPath()).isEqualTo("§1/tbl1/r2c1");
            assertThat(f.textExcerpt()).isEqualTo("阿司匹林");
            assertThat(f.anchorLabel()).isEqualTo("用法用量");
        });
    }

    /** 由 POI 确定性构造的夹具 docx：编号标题 §1 + 1 段正文 + 该节下 1 张 2×2 表（全单元格非空）。 */
    private byte[] guidelineDocxWithTable() throws IOException {
        try (XWPFDocument word = new XWPFDocument()) {
            word.createParagraph().createRun().setText("1 用法用量");
            word.createParagraph().createRun().setText("成人口服给药。");
            XWPFTable table = word.createTable(2, 2);
            table.getRow(0).getCell(0).setText("药品");
            table.getRow(0).getCell(1).setText("剂量");
            table.getRow(1).getCell(0).setText("阿司匹林");
            table.getRow(1).getCell(1).setText("100mg");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            word.write(baos);
            return baos.toByteArray();
        }
    }

    @Test
    void reSubmittingSameContentIsIdempotent() {
        RequestContext.restore(new RequestContext.Snapshot("trace-it2", OrgScope.tenant(TENANT), "user-it"));
        Instant now = Instant.now();
        SourceDocument doc = sourceDocuments.save(new SourceDocument(null, TENANT, "SRC-PARSE-IDEM",
            SourceType.GUIDELINE, SourceAuthorityLevel.B_GUIDELINE, "依据", "指南", "卫健委", "lic", "zh-CN",
            now, "u", now, "u"));
        DocumentParseRequest request = new DocumentParseRequest(
            doc.id(), "v1", "g.md", DocumentFormat.STRUCTURED_TEXT, CONTENT);

        DocParseJob first = service.submit(request);
        DocParseJob second = service.submit(request);

        assertThat(second.status()).isEqualTo(ParseJobStatus.SUCCEEDED);
        assertThat(second.resultSourceVersionId()).isEqualTo(first.resultSourceVersionId());
        assertThat(second.parsedFragmentCount()).isZero();
    }
}
