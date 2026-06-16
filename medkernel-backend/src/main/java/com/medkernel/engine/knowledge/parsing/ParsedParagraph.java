package com.medkernel.engine.knowledge.parsing;

/**
 * 解析出的段落（AIK-STD-02）。{@code text} 为段落正文，{@code page} 为来源页号（1 基）；
 * 文本/Word 正文无版式页维度时 {@code page} 为 {@code null}（锚点不含 {@code p} 前缀），
 * PDF 逐页提取时携真实页号，物化为 {@code p<页>/§<节>/¶<段>} 页锚点（铁律 #1：页号须为真实提取来源）。
 */
public record ParsedParagraph(
    String text,
    Integer page
) {
}
