package com.medkernel.engine.knowledge.parsing;

/**
 * 文档解析端口（AIK-STD-02）。按格式分派：{@link #supports} 声明可解析格式，
 * {@link #parse} 解析为章节树；无法解析（损坏/空/不支持）抛 {@link DocumentParseException}（FR-5 诚实失败）。
 */
public interface DocumentParser {

    boolean supports(DocumentFormat format);

    ParsedDocument parse(ParseInput input);
}
