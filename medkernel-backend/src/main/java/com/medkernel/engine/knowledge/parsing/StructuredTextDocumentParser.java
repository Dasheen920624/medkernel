package com.medkernel.engine.knowledge.parsing;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.medkernel.engine.knowledge.parsing.DocumentSectionizer.TextLine;

/**
 * 结构化文本解析器（AIK-STD-02 B0）。按 UTF-8 解码原文，逐行喂给 {@link DocumentSectionizer}
 * 解析为章节树（Markdown 标题或点分编号标题）。纯文本无版式页维度，故各行页号为 {@code null}
 * （锚点不含 {@code p} 前缀）。空文档由分章器诚实抛错（FR-5）。
 */
@Component
public class StructuredTextDocumentParser implements DocumentParser {

    @Override
    public boolean supports(DocumentFormat format) {
        return format == DocumentFormat.STRUCTURED_TEXT;
    }

    @Override
    public ParsedDocument parse(ParseInput input) {
        String text = new String(input.rawBytes(), UTF_8);
        List<TextLine> lines = new ArrayList<>();
        for (String raw : text.split("\\R", -1)) {
            String line = raw.strip();
            if (!line.isEmpty()) {
                lines.add(new TextLine(line, null));
            }
        }
        return DocumentSectionizer.sectionize(lines);
    }
}
