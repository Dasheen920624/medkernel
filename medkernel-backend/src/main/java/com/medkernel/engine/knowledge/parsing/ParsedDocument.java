package com.medkernel.engine.knowledge.parsing;

import java.util.List;

/**
 * 解析产物（AIK-STD-02 FR-1）：扁平章节列表（numberPath 编码树位置），
 * 物化器据此落带锚点的来源片段。
 */
public record ParsedDocument(
    List<ParsedSection> sections
) {
}
