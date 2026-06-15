package com.medkernel.engine.knowledge.parsing;

import java.util.List;

/**
 * 解析出的章节（AIK-STD-02 FR-1）。{@code numberPath} 编码层级（如 "2.1.3"；
 * 标题前正文归入根序 "0"=前言），{@code level} 为层级深度，章节树以 numberPath 前缀关系隐式表达。
 * {@code paragraphs} 为章节正文段落，物化时逐段落地为带锚点的来源片段。
 */
public record ParsedSection(
    String numberPath,
    int level,
    String title,
    List<String> paragraphs
) {
}
