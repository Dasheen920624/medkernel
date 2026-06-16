package com.medkernel.engine.knowledge.parsing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import com.medkernel.engine.knowledge.parsing.DocumentSectionizer.TextLine;

/**
 * PDF 解析器（AIK-STD-02 PR2，Apache PDFBox）。逐页确定性提取文本层 → 每行携真实页号（1 基）→
 * 复用 {@link DocumentSectionizer} 解析为章节树，物化为带 {@code p<页>/§<节>/¶<段>} 页锚点的来源片段。
 * 纯文本提取、无模型（B0）；无文本层的扫描件不做 OCR（受 P6 + LLM 闸，本卡不实现）→ 诚实失败，
 * 损坏 PDF 亦诚实失败，绝不产伪结构（FR-5 / 铁律 #1）。
 */
@Component
public class PdfDocumentParser implements DocumentParser {

    @Override
    public boolean supports(DocumentFormat format) {
        return format == DocumentFormat.PDF;
    }

    @Override
    public ParsedDocument parse(ParseInput input) {
        List<TextLine> lines = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(input.rawBytes())) {
            int pageCount = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(document);
                for (String raw : pageText.split("\\R", -1)) {
                    String line = raw.strip();
                    if (!line.isEmpty()) {
                        lines.add(new TextLine(line, page));
                    }
                }
            }
        } catch (IOException e) {
            throw new DocumentParseException("PDF 解析失败：" + e.getMessage());
        }
        if (lines.isEmpty()) {
            throw new DocumentParseException("PDF 无可提取文本（可能为扫描件，本卡不做 OCR），诚实拒绝");
        }
        return DocumentSectionizer.sectionize(lines);
    }
}
