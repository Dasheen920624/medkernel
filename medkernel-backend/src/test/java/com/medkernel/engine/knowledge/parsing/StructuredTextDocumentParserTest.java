package com.medkernel.engine.knowledge.parsing;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class StructuredTextDocumentParserTest {

    private final StructuredTextDocumentParser parser = new StructuredTextDocumentParser();

    private ParseInput input(String text) {
        return new ParseInput(1L, "v1", "f.txt", DocumentFormat.STRUCTURED_TEXT, text.getBytes(UTF_8), "tester");
    }

    @Test
    void supportsOnlyStructuredText() {
        assertThat(parser.supports(DocumentFormat.STRUCTURED_TEXT)).isTrue();
        assertThat(parser.supports(DocumentFormat.PDF)).isFalse();
        assertThat(parser.supports(DocumentFormat.WORD)).isFalse();
    }

    @Test
    void parsesMarkdownHeadingsIntoChapterTree() {
        ParsedDocument doc = parser.parse(input("""
            # 总则
            本指南适用于成人。

            ## 适应证
            用于确诊患者。
            """));
        assertThat(doc.sections()).hasSize(2);
        ParsedSection top = doc.sections().get(0);
        assertThat(top.numberPath()).isEqualTo("1");
        assertThat(top.level()).isEqualTo(1);
        assertThat(top.title()).isEqualTo("总则");
        assertThat(top.paragraphs()).extracting(ParsedParagraph::text).containsExactly("本指南适用于成人。");
        assertThat(top.paragraphs()).extracting(ParsedParagraph::page).containsOnlyNulls();
        ParsedSection sub = doc.sections().get(1);
        assertThat(sub.numberPath()).isEqualTo("1.1");
        assertThat(sub.level()).isEqualTo(2);
        assertThat(sub.title()).isEqualTo("适应证");
        assertThat(sub.paragraphs()).extracting(ParsedParagraph::text).containsExactly("用于确诊患者。");
    }

    @Test
    void parsesNumberedHeadings() {
        ParsedDocument doc = parser.parse(input("""
            1 适用范围
            适用于二级以上医院。
            1.1 人群
            成人患者。
            """));
        assertThat(doc.sections()).extracting(ParsedSection::numberPath)
            .containsExactly("1", "1.1");
        assertThat(doc.sections().get(1).title()).isEqualTo("人群");
    }

    @Test
    void leadingTextBeforeFirstHeadingGoesToPreambleSectionZero() {
        ParsedDocument doc = parser.parse(input("""
            前置说明无标题。
            # 第一章
            正文。
            """));
        assertThat(doc.sections().get(0).numberPath()).isEqualTo("0");
        assertThat(doc.sections().get(0).title()).isEqualTo("前言");
        assertThat(doc.sections().get(0).paragraphs()).extracting(ParsedParagraph::text)
            .containsExactly("前置说明无标题。");
    }

    @Test
    void splitsParagraphExceedingExcerptLimitWithoutLosingContent() {
        String longPara = "句一。" + "字".repeat(2100) + "。";
        ParsedDocument doc = parser.parse(input("# 标题\n" + longPara + "\n"));
        List<String> paras = doc.sections().get(0).paragraphs().stream().map(ParsedParagraph::text).toList();
        assertThat(paras).hasSizeGreaterThan(1);
        assertThat(paras).allMatch(p -> p.length() <= 2048);
        assertThat(String.join("", paras)).isEqualTo(longPara);
    }

    @Test
    void blankInputFailsHonestly() {
        assertThatThrownBy(() -> parser.parse(input("   \n  \n")))
            .isInstanceOf(DocumentParseException.class)
            .hasMessageContaining("空文档");
    }
}
