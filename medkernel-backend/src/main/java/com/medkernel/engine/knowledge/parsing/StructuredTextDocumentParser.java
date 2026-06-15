package com.medkernel.engine.knowledge.parsing;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * 结构化文本解析器（AIK-STD-02 B0）。纯规则、确定性、零外部依赖：
 * 识别 Markdown 标题（{@code #}/{@code ##}…）与点分编号标题（{@code 1}/{@code 1.1}），
 * 解析为章节树；标题前正文归入「前言」§0；超出片段长度上限的段落按句界切分不丢语义。
 */
@Component
public class StructuredTextDocumentParser implements DocumentParser {

    private static final int MAX_EXCERPT = 2048;
    private static final Pattern MARKDOWN = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");
    private static final Pattern NUMBERED = Pattern.compile("^(\\d+(?:\\.\\d+)*)[\\s、.]+(.+?)\\s*$");

    @Override
    public boolean supports(DocumentFormat format) {
        return format == DocumentFormat.STRUCTURED_TEXT;
    }

    @Override
    public ParsedDocument parse(ParseInput input) {
        String text = new String(input.rawBytes(), UTF_8);
        if (text.isBlank()) {
            throw new DocumentParseException("空文档无法解析，禁止产伪结构");
        }
        List<ParsedSection> sections = new ArrayList<>();
        // 自动编号计数器（Markdown 用，下标 = 层级）
        int[] counters = new int[7];
        SectionBuilder current = null;
        List<String> preamble = new ArrayList<>();

        for (String raw : text.split("\\R", -1)) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            Heading heading = detectHeading(line, counters);
            if (heading != null) {
                if (current != null) {
                    sections.add(current.build());
                }
                current = new SectionBuilder(heading.numberPath(), heading.level(), heading.title());
            } else if (current == null) {
                preamble.add(line);
            } else {
                current.addParagraph(line);
            }
        }
        if (current != null) {
            sections.add(current.build());
        }
        if (!preamble.isEmpty()) {
            SectionBuilder pre = new SectionBuilder("0", 1, "前言");
            preamble.forEach(pre::addParagraph);
            sections.add(0, pre.build());
        }
        if (sections.isEmpty()) {
            throw new DocumentParseException("空文档无法解析，禁止产伪结构");
        }
        return new ParsedDocument(sections);
    }

    private Heading detectHeading(String line, int[] counters) {
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

    private record Heading(String numberPath, int level, String title) {
    }

    /** 章节累积器，段落超长按句界切分（。！？.!?）不丢语义。 */
    private static final class SectionBuilder {
        private final String numberPath;
        private final int level;
        private final String title;
        private final List<String> paragraphs = new ArrayList<>();

        SectionBuilder(String numberPath, int level, String title) {
            this.numberPath = numberPath;
            this.level = level;
            this.title = title;
        }

        void addParagraph(String para) {
            if (para.length() <= MAX_EXCERPT) {
                paragraphs.add(para);
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
                paragraphs.add(para.substring(start, end));
                start = end;
            }
        }

        private int lastSentenceBoundary(String s, int from, int to) {
            for (int i = to - 1; i > from; i--) {
                char c = s.charAt(i);
                if (c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?') {
                    return i + 1;
                }
            }
            return to;
        }

        ParsedSection build() {
            return new ParsedSection(numberPath, level, title, List.copyOf(paragraphs));
        }
    }
}
