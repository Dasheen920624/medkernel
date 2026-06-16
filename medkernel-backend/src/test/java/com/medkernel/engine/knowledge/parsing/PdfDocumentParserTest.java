package com.medkernel.engine.knowledge.parsing;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

/**
 * PDF 解析器测试（AIK-STD-02 PR2，Apache PDFBox）。夹具 PDF 由 PDFBox 确定性构造（ASCII 编号标题 +
 * 段落，避免 CJK 字体内嵌），验证：逐页提取 → 章节树 + 逐段真实页号；扫描件（无文本层）与损坏 PDF 诚实失败。
 */
class PdfDocumentParserTest {

    private final PdfDocumentParser parser = new PdfDocumentParser();

    private ParseInput pdfInput(byte[] bytes) {
        return new ParseInput(1L, "v1", "g.pdf", DocumentFormat.PDF, bytes, "tester");
    }

    /** 逐页写若干行（每行一个 showText，行距确定），返回 PDF 字节。 */
    private byte[] pdf(List<List<String>> pages) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (List<String> lines : pages) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
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
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    @Test
    void supportsOnlyPdf() {
        assertThat(parser.supports(DocumentFormat.PDF)).isTrue();
        assertThat(parser.supports(DocumentFormat.STRUCTURED_TEXT)).isFalse();
        assertThat(parser.supports(DocumentFormat.WORD)).isFalse();
    }

    @Test
    void parsesNumberedSectionsWithRealPageNumbers() throws IOException {
        byte[] bytes = pdf(List.of(
            List.of("1 Scope", "This guideline applies to adults.", "1.1 Population", "Adult patients only."),
            List.of("2 Treatment", "First line therapy is drug A.")));

        ParsedDocument parsed = parser.parse(pdfInput(bytes));

        assertThat(parsed.sections()).extracting(ParsedSection::numberPath)
            .containsExactly("1", "1.1", "2");

        ParsedSection scope = parsed.sections().get(0);
        assertThat(scope.title()).isEqualTo("Scope");
        assertThat(scope.paragraphs()).singleElement()
            .satisfies(p -> {
                assertThat(p.text()).contains("adults");
                assertThat(p.page()).isEqualTo(1);
            });

        ParsedSection population = parsed.sections().get(1);
        assertThat(population.paragraphs()).extracting(ParsedParagraph::page).containsOnly(1);

        ParsedSection treatment = parsed.sections().get(2);
        assertThat(treatment.title()).isEqualTo("Treatment");
        assertThat(treatment.paragraphs()).singleElement()
            .satisfies(p -> {
                assertThat(p.text()).contains("First line therapy");
                assertThat(p.page()).isEqualTo(2);
            });
    }

    @Test
    void emptyTextPdfFailsHonestlyWithoutOcr() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.A4));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            assertThatThrownBy(() -> parser.parse(pdfInput(baos.toByteArray())))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("无可提取文本");
        }
    }

    @Test
    void corruptedBytesFailHonestly() {
        assertThatThrownBy(() -> parser.parse(pdfInput("this is not a pdf".getBytes(UTF_8))))
            .isInstanceOf(DocumentParseException.class)
            .hasMessageContaining("PDF");
    }
}
