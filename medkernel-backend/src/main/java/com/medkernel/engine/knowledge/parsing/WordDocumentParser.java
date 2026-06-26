package com.medkernel.engine.knowledge.parsing;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import com.medkernel.engine.knowledge.parsing.DocumentSectionizer.Element;
import com.medkernel.engine.knowledge.parsing.DocumentSectionizer.TableBlock;
import com.medkernel.engine.knowledge.parsing.DocumentSectionizer.TextLine;

/**
 * Word 解析器（AIK-STD-02，Apache POI）。按文档顺序遍历 {@code .docx} 正文元素：段落确定性提取为
 * 文本行（无版式页维度故页号为 {@code null}）、表格按结构（行/单元格）提取为表格块 → 复用
 * {@link DocumentSectionizer} 解析为章节树 + 表格理解（表格归属当前章节，物化为单元锚点片段）。
 * 纯结构读取、无模型（B0）；旧二进制 {@code .doc}、非 OOXML 字节、损坏 docx 诚实失败，
 * 无正文内容亦诚实失败，绝不产伪结构（FR-5 / 铁律 #1）。
 */
@Component
public class WordDocumentParser implements DocumentParser {

    @Override
    public boolean supports(DocumentFormat format) {
        return format == DocumentFormat.WORD;
    }

    @Override
    public ParsedDocument parse(ParseInput input) {
        List<Element> elements = new ArrayList<>();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(input.rawBytes()))) {
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = paragraph.getText().strip();
                    if (!text.isEmpty()) {
                        elements.add(new TextLine(text, null));
                    }
                } else if (element instanceof XWPFTable table) {
                    elements.add(new TableBlock(null, readRows(table)));
                }
            }
        } catch (IOException | POIXMLException | NotOfficeXmlFileException e) {
            throw new DocumentParseException("Word 解析失败：" + e.getMessage());
        }
        if (elements.isEmpty()) {
            throw new DocumentParseException("Word 文档无可提取文本内容，诚实拒绝");
        }
        return DocumentSectionizer.sectionize(elements);
    }

    /** 按结构逐行逐单元格读取表格正文（行优先矩阵），单元格正文 strip 去边界空白。 */
    private List<List<String>> readRows(XWPFTable table) {
        List<List<String>> rows = new ArrayList<>();
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                cells.add(cell.getText().strip());
            }
            rows.add(cells);
        }
        return rows;
    }
}
