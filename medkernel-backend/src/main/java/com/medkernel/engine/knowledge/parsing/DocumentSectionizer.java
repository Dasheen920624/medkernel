package com.medkernel.engine.knowledge.parsing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档分章器（AIK-STD-02 B0）。把有序文档元素流（文本行携来源页号 + 表格块）确定性地解析为
 * 章节树 + 表格列表：识别 Markdown 标题（{@code #}/{@code ##}…）与点分编号标题（{@code 1}/{@code 1.1}），
 * 标题前正文归入「前言」§0；超出片段长度上限的段落按句界切分不丢语义；段落沿用其来源页号；
 * 表格归属其出现处的当前章节并按节内出现序编号（{@code tbl<n>}）。
 * 纯规则、确定性、零外部依赖，供文本/PDF/Word 各解析器共用，去重解析骨架。
 * 元素流无任何可成章/成表内容时诚实抛 {@link DocumentParseException}（FR-5，绝不产伪结构）。
 */
final class DocumentSectionizer {

    private static final int MAX_EXCERPT = 2048;
    private static final String PREAMBLE_PATH = "0";
    private static final String PREAMBLE_TITLE = "前言";
    private static final Pattern MARKDOWN = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");
    private static final Pattern NUMBERED = Pattern.compile("^(\\d+(?:\\.\\d+)*)[\\s、.]+(.+?)\\s*$");

    private DocumentSectionizer() {
    }

    /** 文档元素：有序流中的一个单元，文本行或表格块，供分章器统一消费。 */
    sealed interface Element permits TextLine, TableBlock {
    }

    /** 文本行：正文 + 来源页号（1 基；无版式页维度时为 {@code null}）。 */
    record TextLine(String text, Integer page) implements Element {
    }

    /** 表格块：行优先单元格正文矩阵 + 来源页号（1 基；无版式页维度时为 {@code null}）。 */
    record TableBlock(Integer page, List<List<String>> rows) implements Element {
    }

    static ParsedDocument sectionize(List<Element> elements) {
        List<ParsedSection> sections = new ArrayList<>();
        List<ParsedTable> tables = new ArrayList<>();
        Map<String, Integer> tableSeq = new HashMap<>();
        int[] counters = new int[7];
        SectionBuilder current = null;
        String currentNumberPath = PREAMBLE_PATH;
        String currentTitle = PREAMBLE_TITLE;
        List<ParsedParagraph> preamble = new ArrayList<>();

        for (Element element : elements) {
            if (element instanceof TextLine line) {
                String text = line.text().strip();
                if (text.isEmpty()) {
                    continue;
                }
                Heading heading = detectHeading(text, counters);
                if (heading != null) {
                    if (current != null) {
                        sections.add(current.build());
                    }
                    current = new SectionBuilder(heading.numberPath(), heading.level(), heading.title());
                    currentNumberPath = heading.numberPath();
                    currentTitle = heading.title();
                } else if (current == null) {
                    appendParagraph(preamble, text, line.page());
                } else {
                    current.addParagraph(text, line.page());
                }
            } else if (element instanceof TableBlock block) {
                int seq = tableSeq.merge(currentNumberPath, 1, Integer::sum);
                tables.add(new ParsedTable(currentNumberPath, currentTitle, seq, block.page(), block.rows()));
            }
        }
        if (current != null) {
            sections.add(current.build());
        }
        if (!preamble.isEmpty()) {
            sections.add(0, new ParsedSection(PREAMBLE_PATH, 1, PREAMBLE_TITLE, List.copyOf(preamble)));
        }
        if (sections.isEmpty() && tables.isEmpty()) {
            throw new DocumentParseException("空文档无法解析，禁止产伪结构");
        }
        return new ParsedDocument(sections, tables);
    }

    private static Heading detectHeading(String line, int[] counters) {
        Matcher md = MARKDOWN.matcher(line);
        if (md.matches()) {
            int level = md.group(1).length();
            counters[level]++;
            for (int i = level + 1; i < counters.length; i++) {
                counters[i] = 0;
            }
            StringBuilder path = new StringBuilder();
            for (int i = 1; i <= level; i++) {
                if (counters[i] == 0) {
                    counters[i] = 1;
                }
                if (i > 1) {
                    path.append('.');
                }
                path.append(counters[i]);
            }
            return new Heading(path.toString(), level, md.group(2).strip());
        }
        Matcher num = NUMBERED.matcher(line);
        if (num.matches()) {
            String numberPath = num.group(1);
            int level = numberPath.split("\\.").length;
            return new Heading(numberPath, level, num.group(2).strip());
        }
        return null;
    }

    /** 把（可能超长的）段落按句界切分后逐片追加，各片沿用同一来源页号。 */
    private static void appendParagraph(List<ParsedParagraph> sink, String para, Integer page) {
        if (para.length() <= MAX_EXCERPT) {
            sink.add(new ParsedParagraph(para, page));
            return;
        }
        int start = 0;
        while (start < para.length()) {
            int end = Math.min(start + MAX_EXCERPT, para.length());
            if (end < para.length()) {
                int cut = lastSentenceBoundary(para, start, end);
                if (cut > start) {
                    end = cut;
                }
            }
            sink.add(new ParsedParagraph(para.substring(start, end), page));
            start = end;
        }
    }

    private static int lastSentenceBoundary(String s, int from, int to) {
        for (int i = to - 1; i > from; i--) {
            char c = s.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?') {
                return i + 1;
            }
        }
        return to;
    }

    private record Heading(String numberPath, int level, String title) {
    }

    /** 章节累积器，逐段携页号累积。 */
    private static final class SectionBuilder {
        private final String numberPath;
        private final int level;
        private final String title;
        private final List<ParsedParagraph> paragraphs = new ArrayList<>();

        SectionBuilder(String numberPath, int level, String title) {
            this.numberPath = numberPath;
            this.level = level;
            this.title = title;
        }

        void addParagraph(String para, Integer page) {
            appendParagraph(paragraphs, para, page);
        }

        ParsedSection build() {
            return new ParsedSection(numberPath, level, title, List.copyOf(paragraphs));
        }
    }
}
