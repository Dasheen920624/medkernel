package com.medkernel.engine.knowledge.parsing;

import java.util.List;

/**
 * 解析出的表格（AIK-STD-02 FR-2 表格理解）。归属某章节（{@code sectionNumberPath} 节号 +
 * {@code sectionTitle} 节标题），{@code indexInSection} 为该节内 1 基表序（编码锚点 {@code tbl<n>}），
 * {@code page} 为来源页号（PDF 等版式来源 1 基；文本/Word 无版式页维度时为 {@code null}）。
 * {@code rows} 为行优先的单元格正文矩阵，物化时逐非空单元格落为带 {@code [p<页>/]§<节>/tbl<n>/r<行>c<列>}
 * 锚点的来源片段（铁律 #1：仅落真实抽取的单元格，空单元格不产指纹）。
 */
public record ParsedTable(
    String sectionNumberPath,
    String sectionTitle,
    int indexInSection,
    Integer page,
    List<List<String>> rows
) {
}
