package com.medkernel.engine.knowledge.parsing;

import java.util.List;

/**
 * 解析产物（AIK-STD-02 FR-1/FR-2）：扁平章节列表（numberPath 编码树位置）+ 表格列表（各归属章节），
 * 物化器据此落带锚点的来源片段（章节段落锚点 {@code §节/¶段}、表格单元锚点 {@code §节/tbl<n>/r<行>c<列>}）。
 */
public record ParsedDocument(
    List<ParsedSection> sections,
    List<ParsedTable> tables
) {
}
