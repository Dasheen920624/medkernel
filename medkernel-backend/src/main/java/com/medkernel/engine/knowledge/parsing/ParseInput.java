package com.medkernel.engine.knowledge.parsing;

/**
 * 解析输入（AIK-STD-02）。稳定端口入参，覆盖全格式：原始字节 + 文件名 + 声明格式。
 * PR1 STRUCTURED_TEXT 解析器按 UTF-8 解码 {@code rawBytes}；后续 PR 的二进制格式直接读字节。
 */
public record ParseInput(
    Long sourceDocumentId,
    String versionNo,
    String fileName,
    DocumentFormat format,
    byte[] rawBytes,
    String createdBy
) {
}
