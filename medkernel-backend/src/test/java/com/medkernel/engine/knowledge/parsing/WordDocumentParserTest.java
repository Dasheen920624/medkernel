package com.medkernel.engine.knowledge.parsing;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

/**
 * Word 解析器测试（AIK-STD-02 PR3，Apache POI）。夹具 {@code .docx} 由 POI 确定性构造（编号标题 +
 * 段落 + 表格），验证：段落归章节（无版式页 page=null）+ 表格理解（表→行/单元格、归属当前节、节内表序）；
 * 空 docx 与损坏字节诚实失败（FR-5 / 铁律 #1，绝不产伪结构）。
 */
class WordDocumentParserTest {

    private final WordDocumentParser parser = new WordDocumentParser();

    private ParseInput wordInput(byte[] bytes) {
        return new ParseInput(1L, "v1", "g.docx", DocumentFormat.WORD, bytes, "tester");
    }

    /** 由 POI 构造的夹具 docx：编号标题 §1 + 1 段正文 + 该节下 1 张 2×2 表。 */
    private byte[] guidelineDocx() throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.createParagraph().createRun().setText("1 用法用量");
            doc.createParagraph().createRun().setText("口服给药。");
            XWPFTable table = doc.createTable(2, 2);
            table.getRow(0).getCell(0).setText("药品");
            table.getRow(0).getCell(1).setText("剂量");
            table.getRow(1).getCell(0).setText("阿司匹林");
            table.getRow(1).getCell(1).setText("100mg");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.write(baos);
            return baos.toByteArray();
        }
    }

    @Test
    void supportsOnlyWord() {
        assertThat(parser.supports(DocumentFormat.WORD)).isTrue();
        assertThat(parser.supports(DocumentFormat.PDF)).isFalse();
        assertThat(parser.supports(DocumentFormat.STRUCTURED_TEXT)).isFalse();
    }

    @Test
    void parsesHeadingsParagraphsAndTableUnderItsSection() throws IOException {
        ParsedDocument parsed = parser.parse(wordInput(guidelineDocx()));

        assertThat(parsed.sections()).extracting(ParsedSection::numberPath).containsExactly("1");
        ParsedSection section = parsed.sections().get(0);
        assertThat(section.title()).isEqualTo("用法用量");
        assertThat(section.paragraphs()).singleElement().satisfies(p -> {
            assertThat(p.text()).isEqualTo("口服给药。");
            assertThat(p.page()).isNull();
        });

        assertThat(parsed.tables()).singleElement().satisfies(t -> {
            assertThat(t.sectionNumberPath()).isEqualTo("1");
            assertThat(t.sectionTitle()).isEqualTo("用法用量");
            assertThat(t.indexInSection()).isEqualTo(1);
            assertThat(t.page()).isNull();
            assertThat(t.rows()).containsExactly(
                List.of("药品", "剂量"), List.of("阿司匹林", "100mg"));
        });
    }

    @Test
    void emptyDocxFailsHonestly() throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.write(baos);
            assertThatThrownBy(() -> parser.parse(wordInput(baos.toByteArray())))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("无可提取");
        }
    }

    @Test
    void corruptedBytesFailHonestly() {
        assertThatThrownBy(() -> parser.parse(wordInput("this is not a docx".getBytes(UTF_8))))
            .isInstanceOf(DocumentParseException.class)
            .hasMessageContaining("Word");
    }
}
